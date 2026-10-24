#!/usr/bin/env node
/**
 * 调 jnf-harness Agent 流式接口（POST /api/agent/stream），逐帧解析 SSE 并打印工具轨迹与最终回答。
 * 用法：
 *   node agent_stream.mjs [--base http://127.0.0.1:8090] [--api-key <key>] \
 *     [--agent app-e2e] [--message "你好"] [--model <model>] [--timeout 120]
 * 输出三段：① 流式事件（step_break 去重 / tool_result 摘要，各截断 200 字）② done.text 最终回答 ③ 汇总。
 * 退出码：0 = 收到 done 且无 error；1 = error 事件 / 流异常结束 / 超时。
 * 零 npm 依赖。
 */
import http from 'node:http';
import https from 'node:https';

/**
 * POST 并返回流式响应（Node 内置 http/https）。
 * 不用 fetch：Node 24 Windows 在连接池仍有 handle 时强制 exit 会触发 libuv 断言。
 */
function postStream(url, headers, body, timeoutMs) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const lib = target.protocol === 'https:' ? https : http;
    const fail = (message) => {
      const error = new Error(message);
      error.code = 'ETIMEDOUT';
      request.destroy(error);
    };
    const request = lib.request(target, { method: 'POST', headers }, (response) => {
      resolve({ status: response.statusCode || 0, response, request });
    });
    const timer = setTimeout(() => fail(`整体超时（${timeoutMs}ms）`), timeoutMs);
    timer.unref();
    request.setTimeout(timeoutMs, () => fail(`空闲超时（${timeoutMs}ms）`));
    request.on('error', reject);
    request.on('close', () => clearTimeout(timer));
    request.write(body);
    request.end();
  });
}

const HELP = `流式调用 jnf-harness Agent 并解析 SSE（meta/chunk/thinking/step_break/tool_result/usage/finish/done/error）

用法:
  node agent_stream.mjs [选项] [消息...]
选项:
  --base <url>       底座地址，默认 http://127.0.0.1:8090
  --api-key <key>    底座启用鉴权时的 X-API-Key
  --agent <id>       agentId，默认 app-e2e
  --message <文本>   用户消息（也可直接放在末尾作为位置参数：agent_stream.mjs --base ... "你好"）
  --model <model>    指定模型（可选，不给用底座默认渠道）
  --cwd <dir>        Agent 工作目录（可选，默认底座进程 cwd）
  --timeout <秒>     整体超时，默认 120
  --help, -h         显示本帮助

示例:
  node agent_stream.mjs --base http://127.0.0.1:8090 --agent app-e2e "列出示例应用里的全部条目"
  node agent_stream.mjs --api-key <key> --message "查一下今天的数据汇总"
退出码: 0 = 收到 done；1 = error 事件、流未正常结束或超时。`;

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--help' || arg === '-h') {
      out.help = true;
    } else if (arg.startsWith('--')) {
      const eq = arg.indexOf('=');
      if (eq > 0) {
        out[arg.slice(2, eq)] = arg.slice(eq + 1);
      } else {
        const next = argv[i + 1];
        if (next === undefined || next.startsWith('--')) {
          out[arg.slice(2)] = true;
        } else {
          out[arg.slice(2)] = next;
          i++;
        }
      }
    } else {
      out._.push(arg);
    }
  }
  return out;
}

const opts = parseArgs(process.argv.slice(2));
if (opts.help) {
  console.log(HELP);
  process.exit(0);
}

const base = String(opts.base || 'http://127.0.0.1:8090').replace(/\/+$/, '');
const apiKey = typeof opts['api-key'] === 'string' ? opts['api-key'] : '';
const agentId = typeof opts.agent === 'string' ? opts.agent : 'app-e2e';
const timeoutSec = Math.max(5, Number(opts.timeout || 120));
const positional = opts._.join(' ').trim();
const message = (typeof opts.message === 'string' ? opts.message : positional) || '你好，请先列出你能调用的工具，再调用其中一个；没有可调用工具就直接回答。';

const truncate = (value, limit = 200) => {
  const text = String(value ?? '').replace(/\s+/g, ' ').trim();
  return text.length <= limit ? text : `${text.slice(0, limit)}…（共 ${text.length} 字）`;
};

/** 解析单个 SSE 帧；注释帧（keepalive）与无 data 帧返回 null。 */
function parseFrame(raw) {
  let event = 'message';
  const dataLines = [];
  for (const line of raw.split('\n')) {
    if (line === '' || line.startsWith(':')) continue; // 空行 / ": keepalive" 注释
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') dataLines.push(value);
    // id / retry 字段忽略
  }
  if (dataLines.length === 0) return null;
  return { event, data: dataLines.join('\n') };
}

const state = {
  meta: null,
  finish: null,
  done: null,
  errors: [],
  chunks: '',
  thinkingChars: 0,
  usage: null,
  seenStepBreak: new Set(),
  toolCalls: new Set(),
  stepBreakCount: 0,
  toolResultCount: 0,
};

function handleFrame(frame) {
  let payload;
  try {
    payload = JSON.parse(frame.data);
  } catch {
    return; // 非 JSON 数据帧忽略
  }
  const event = frame.event && frame.event !== 'message' ? frame.event : payload?.event || payload?.type || 'message';
  switch (event) {
    case 'meta':
      state.meta = payload;
      console.log(`[meta]       agentId=${payload.agentId ?? '?'}`);
      break;
    case 'chunk':
      state.chunks += String(payload.content ?? '');
      break;
    case 'thinking':
      state.thinkingChars += String(payload.content ?? '').length;
      break;
    case 'step_break': {
      state.stepBreakCount++;
      // step_break 可能重复推送：callId+工具+参数+状态完全相同才视为重复
      const key = `${payload.callId ?? ''}|${payload.toolName ?? ''}|${payload.args ?? ''}|${payload.status ?? ''}`;
      if (state.seenStepBreak.has(key)) return;
      state.seenStepBreak.add(key);
      const callKey = payload.callId || `${payload.toolName ?? ''}:${state.stepBreakCount}`;
      state.toolCalls.add(callKey);
      console.log(`[step_break] → ${payload.toolName ?? '?'}  callId=${payload.callId ?? '-'} status=${payload.status ?? '-'}`);
      console.log(`             args: ${truncate(payload.args, 200)}`);
      break;
    }
    case 'tool_result':
      state.toolResultCount++;
      console.log(`[tool_result] ← ${payload.toolName ?? '?'}  error=${payload.error === true}`);
      console.log(`             result: ${truncate(payload.result, 200)}`);
      break;
    case 'usage':
      state.usage = payload;
      console.log(
        `[usage]      prompt=${payload.promptTokens ?? '?'} completion=${payload.completionTokens ?? '?'}` +
          ` total=${payload.totalTokens ?? '?'} 耗时=${payload.elapsedMs ?? '?'}ms`,
      );
      break;
    case 'finish':
      state.finish = payload;
      console.log(`[finish]     turn=${payload.turn ?? '?'}`);
      break;
    case 'done':
      state.done = payload;
      break;
    case 'error':
      state.errors.push(payload);
      console.log(`[error]      code=${payload.code ?? 'STREAM_ERROR'} message=${payload.message ?? '(空)'}`);
      break;
    default:
      console.log(`[${event}] ${truncate(frame.data, 160)}`);
  }
}

const url = `${base}/api/agent/stream`;
const requestBody = { agentId, message };
if (typeof opts.model === 'string' && opts.model) requestBody.model = opts.model;
if (typeof opts.cwd === 'string' && opts.cwd) requestBody.cwd = opts.cwd;
const bodyJson = JSON.stringify(requestBody);

console.log(`[INFO] POST ${url}`);
console.log(`[INFO] agentId=${agentId}  timeout=${timeoutSec}s  message="${truncate(message, 120)}"`);
console.log('== 流式事件 ==');

let httpStatus = 0;
try {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'Content-Length': Buffer.byteLength(bodyJson),
  };
  if (apiKey) headers['X-API-Key'] = apiKey;
  const { status, response } = await postStream(url, headers, bodyJson, timeoutSec * 1000);
  httpStatus = status;
  if (status !== 200) {
    const text = await new Promise((resolve) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
      response.on('error', () => resolve(''));
    });
    if (status === 401) {
      throw new Error('底座拒绝了请求（HTTP 401）：需要 API Key，请加 --api-key <key>');
    }
    throw new Error(`HTTP ${status}：${truncate(text, 300)}`);
  }

  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  for await (const chunk of response) {
    buffer += decoder.decode(chunk, { stream: true });
    buffer = buffer.replace(/\r\n/g, '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const frame = parseFrame(raw);
      if (frame) handleFrame(frame);
    }
  }
  buffer += decoder.decode();
  buffer = buffer.replace(/\r\n/g, '\n').trim();
  if (buffer) {
    const frame = parseFrame(buffer);
    if (frame) handleFrame(frame);
  }
} catch (error) {
  console.error('');
  if (error?.code === 'ETIMEDOUT') {
    console.error(`[FAIL] 流式调用超时（${timeoutSec}s）：${url}`);
    console.error('       模型可能响应慢或渠道不通；先确认底座已配模型渠道，或加大 --timeout');
  } else {
    console.error(`[FAIL] 流式调用失败：${error?.message || error}`);
    if (httpStatus === 0) console.error(`       底座是否在 ${base} 运行？先跑 node smoke_test.mjs --base ${base}`);
  }
  if (state.chunks) console.error(`       已收到 ${state.chunks.length} 字增量文本（见下）`);
  printResult();
  process.exit(1);
}

function printResult() {
  console.log('');
  console.log('== 最终回答（done.text） ==');
  const finalText = state.done?.text ?? '';
  if (finalText) {
    console.log(finalText);
  } else if (state.chunks) {
    console.log('（未收到 done，回退展示 chunk 累积文本）');
    console.log(state.chunks);
  } else {
    console.log('（空）');
  }
  console.log('');
  console.log('== 汇总 ==');
  console.log(`收到 done: ${state.done ? '是' : '否'}`);
  if (state.done) {
    console.log(`done.status=${state.done.status ?? '?'}  done.turn=${state.done.turn ?? '?'}  done.toolCalls=${state.done.toolCalls ?? '?'}`);
  }
  if (state.meta) console.log(`agentId=${state.meta.agentId ?? '?'}`);
  console.log(`工具调用次数: ${typeof state.done?.toolCalls === 'number' ? state.done.toolCalls : state.toolCalls.size}（去重后 step_break ${state.toolCalls.size} / 原始 ${state.stepBreakCount}，tool_result ${state.toolResultCount}）`);
  if (state.thinkingChars) console.log(`思考内容: ${state.thinkingChars} 字（--thinking 未输出）`);
  if (state.usage) console.log(`用量: total=${state.usage.totalTokens ?? '?'} tokens / 缓存命中=${state.usage.cacheHitRate ?? '?'}`);
}

printResult();

if (state.errors.length) {
  console.error('');
  console.error(`[FAIL] 收到 ${state.errors.length} 个 error 事件，非零退出。`);
  console.error('       常见原因：未配置模型渠道（控制台「设置 → 模型」）、模型不支持当前 profile、底座与模型网络不通。');
  process.exit(1);
}
if (!state.done) {
  console.error('');
  console.error('[FAIL] 流已结束但未收到 done 事件（可能被中断/超时/上游异常）。');
  console.error('       查看底座日志 logs/harness.log 里最近的 ERROR；确认模型渠道可用后重试。');
  process.exit(1);
}
console.log('');
console.log('[OK] 流式调用完成。');
process.exit(0);
