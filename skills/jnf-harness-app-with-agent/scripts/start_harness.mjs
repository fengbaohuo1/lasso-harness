#!/usr/bin/env node
/**
 * 后台启动 jnf-harness 底座（默认端口 8090），轮询 /actuator/health 直到 UP 后打印地址。
 * 用法：
 *   node start_harness.mjs [--harness <dir>] [--port 8090] [--jar <jar 路径>]
 *                          [--api-key <k1,k2>] [--timeout 90] [--log <日志路径>]
 * 行为：
 *   - 端口已监听且 /actuator/health = UP → 直接复用，不重复启动；
 *   - 否则 detached 后台 spawn `java -jar <jar> --server.port=<port>`，stdout/stderr 写日志文件；
 *   - 脚本退出不 kill 进程（由调用方管理），打印 PID 与停止命令。
 * 零 npm 依赖；Windows / macOS / Linux 通用。
 */
import { spawn, spawnSync } from 'node:child_process';
import { createConnection } from 'node:net';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync } from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import path from 'node:path';

/** Node 内置 http/https 极简请求（不用 fetch：Node 24 Windows 在连接池仍有 handle 时强制 exit 会触发 libuv 断言）。 */
function httpRequest(url, { method = 'GET', headers = {}, body, timeoutMs = 2500 } = {}) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const lib = target.protocol === 'https:' ? https : http;
    const req = lib.request(target, { method, headers }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        text: Buffer.concat(chunks).toString('utf8'),
      }));
      res.on('error', reject);
    });
    req.setTimeout(timeoutMs, () => req.destroy(new Error(`请求超时（${timeoutMs}ms）`)));
    req.on('error', reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}
const HELP = `后台启动 jnf-harness 底座并等待健康检查通过

用法:
  node start_harness.mjs [选项]
选项:
  --harness <dir>    jnf-harness 仓库目录；默认取环境变量 JNF_HARNESS_DIR，再默认 ./jnf-harness
  --port <n>         底座端口，默认 8090
  --jar <path>       底座 jar；默认 <harness>/jnf-harness-app/target/jnf-harness-app.jar
  --api-key <keys>   启用 X-API-Key 鉴权（逗号分隔多个），会作为 --harness.auth.api-keys 传给 JVM
  --timeout <秒>     等待健康检查的超时，默认 90
  --log <path>       日志文件，默认 ./logs/harness.log
  --java <path>      java 可执行文件，默认从 PATH 找 java
  --help, -h         显示本帮助

示例:
  node start_harness.mjs
  node start_harness.mjs --harness jnf-harness --port 18094
  node start_harness.mjs --jar jnf-harness/jnf-harness-app/target/jnf-harness-app.jar --api-key <key>
退出码: 0 = 已启动或已在运行；1 = 启动失败 / 端口被别的服务占用 / 超时。`;

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

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function probePort(port, host = '127.0.0.1', timeoutMs = 800) {
  return new Promise((resolve) => {
    const socket = createConnection({ port, host });
    let settled = false;
    const done = (occupied) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(occupied);
    };
    socket.once('connect', () => done(true));
    socket.once('error', () => done(false));
    socket.setTimeout(timeoutMs, () => done(false));
  });
}

async function probeHealth(base, timeoutMs = 2500) {
  try {
    const res = await httpRequest(`${base}/actuator/health`, {
      timeoutMs,
      headers: { Accept: 'application/json' },
    });
    if (res.status !== 200) {
      return { up: false, detail: `HTTP ${res.status}` };
    }
    try {
      const body = JSON.parse(res.text);
      return { up: body.status === 'UP', detail: res.text.slice(0, 200) };
    } catch {
      return { up: false, detail: res.text.slice(0, 200) };
    }
  } catch (error) {
    return { up: false, detail: error?.message === '请求超时（2500ms）' ? '探测超时' : String(error?.message || error) };
  }
}

/** actuator 不在时（例如裁剪了依赖）的兜底：业务信封接口能通也算活着。 */
async function probeApi(base, apiKey, timeoutMs = 2500) {
  try {
    const headers = { Accept: 'application/json' };
    if (apiKey) headers['X-API-Key'] = apiKey.split(',')[0].trim();
    const res = await httpRequest(`${base}/api/harness/plugins`, { timeoutMs, headers });
    if (res.status !== 200) return { up: false, detail: `HTTP ${res.status}` };
    try {
      const body = JSON.parse(res.text);
      return { up: body.code === '00000', detail: `code=${body.code}` };
    } catch {
      return { up: false, detail: res.text.slice(0, 160) };
    }
  } catch (error) {
    return { up: false, detail: String(error?.message || error) };
  }
}

function readLogTail(logPath, lines = 40) {
  try {
    if (!existsSync(logPath)) return `（日志文件不存在：${logPath}）`;
    const text = readFileSync(logPath, 'utf8');
    return text.split(/\r?\n/).slice(-lines).join('\n');
  } catch (error) {
    return `（读取日志失败：${error.message}）`;
  }
}

function stopHint(pid) {
  return process.platform === 'win32'
    ? `taskkill /PID ${pid} /F`
    : `kill ${pid}`;
}

const opts = parseArgs(process.argv.slice(2));
if (opts.help) {
  console.log(HELP);
  process.exit(0);
}

const port = Number(opts.port || 8090);
if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  console.error(`[FAIL] 端口非法: ${opts.port}`);
  process.exit(1);
}
const timeoutSec = Number(opts.timeout || 90);
const apiKey = typeof opts['api-key'] === 'string' ? opts['api-key'] : '';
const base = `http://127.0.0.1:${port}`;

// ---- 解析 jar ----
const harnessDir = path.resolve(
  typeof opts.harness === 'string' ? opts.harness : process.env.JNF_HARNESS_DIR || 'jnf-harness',
);
const javaBin = typeof opts.java === 'string' ? opts.java : 'java';

let jarPath = '';
if (typeof opts.jar === 'string') {
  jarPath = path.resolve(opts.jar);
} else if (existsSync(path.join(harnessDir, 'jnf-harness-app', 'target', 'jnf-harness-app.jar'))) {
  jarPath = path.join(harnessDir, 'jnf-harness-app', 'target', 'jnf-harness-app.jar');
} else if (path.extname(harnessDir).toLowerCase() === '.jar' && existsSync(harnessDir)) {
  jarPath = harnessDir;
}

console.log(`[INFO] 底座目标: ${base}`);
console.log(`[INFO] jar: ${jarPath || '（未找到，见下方建议）'}`);

// ---- 端口已监听：先探活 ----
if (await probePort(port)) {
  let health = await probeHealth(base);
  if (!health.up) {
    health = await probeApi(base, apiKey);
  }
  if (health.up) {
    console.log(`[SKIP] ${base} 已有底座在运行，直接复用（不是本脚本启动，无 PID）`);
    console.log(`[OK] ${base}  控制台: ${base}/  健康: ${base}/actuator/health`);
    process.exit(0);
  }
  console.error(`[FAIL] 端口 ${port} 已被占用，但健康检查未通过（${health.detail}）`);
  console.error(`       可能是别的程序占用了端口。换端口：--port ${port + 1}；`);
  console.error(`       或先结束占用进程：${process.platform === 'win32'
    ? `Get-NetTCPConnection -LocalPort ${port} -State Listen | Select-Object OwningProcess`
    : `lsof -iTCP:${port} -sTCP:LISTEN`}`);
  process.exit(1);
}

// ---- 校验 java / jar ----
const javaProbe = spawnSync(javaBin, ['-version'], {
  encoding: 'utf8',
  timeout: 20000,
  windowsHide: true,
});
if (javaProbe.error || !`${javaProbe.stdout || ''}${javaProbe.stderr || ''}`.trim()) {
  console.error('[FAIL] 找不到可用的 java，无法启动底座');
  console.error('       安装 JDK 17+：winget install EclipseAdoptium.Temurin.17.JDK（装完重开终端）');
  process.exit(1);
}

if (!jarPath || !existsSync(jarPath)) {
  console.error(`[FAIL] 底座 jar 不存在: ${jarPath || '（未指定）'}`);
  console.error('       先构建：cd <jnf-harness 目录> && mvn -o -q -DskipTests package');
  console.error('       或显式指定：--jar <jnf-harness-app/target/jnf-harness-app.jar>');
  process.exit(1);
}

// ---- 准备日志 ----
const logPath = path.resolve(typeof opts.log === 'string' ? opts.log : path.join('logs', 'harness.log'));
mkdirSync(path.dirname(logPath), { recursive: true });
const logFd = openSync(logPath, 'a');

// ---- 后台启动（显式 --server.port，防 SERVER_PORT 环境变量劫持） ----
const args = ['-jar', jarPath, `--server.port=${port}`];
if (apiKey) {
  args.push(`--harness.auth.api-keys=${apiKey}`);
}
console.log(`[INFO] ${javaBin} ${args.join(' ')}`);
console.log(`[INFO] 日志: ${logPath}`);
console.log(`[INFO] 等待健康检查（最长 ${timeoutSec}s）...`);

let child;
try {
  child = spawn(javaBin, args, {
    detached: true,
    windowsHide: true,
    stdio: ['ignore', logFd, logFd],
  });
} catch (error) {
  console.error(`[FAIL] spawn java 失败: ${error.message}`);
  process.exit(1);
}
child.on('error', (error) => {
  console.error(`[FAIL] 启动 java 失败: ${error.message}`);
});
child.unref();
closeSync(logFd);

const pid = child.pid;
const deadline = Date.now() + timeoutSec * 1000;
let health = { up: false, detail: '尚未开始探测' };
let elapsed = 0;
while (Date.now() < deadline) {
  await sleep(1000);
  elapsed++;
  if (elapsed % 5 === 0) {
    process.stdout.write(`       已等待 ${elapsed}s（查看日志：${logPath}）\n`);
  }
  health = await probeHealth(base);
  if (!health.up) {
    health = await probeApi(base, apiKey);
  }
  if (health.up) break;
}

if (health.up) {
  console.log(`[OK] 底座已启动: ${base}  (pid ${pid})`);
  console.log(`[OK] 控制台: ${base}/   健康: ${base}/actuator/health`);
  if (apiKey) {
    console.log('[INFO] 已启用 API Key 鉴权：后续请求带 X-API-Key 头（脚本传 --api-key）');
  } else {
    console.log('[INFO] 未启用鉴权（harness.auth.api-keys 为空 → 放行本地请求）');
  }
  if (!process.env.LLM_API_KEY && !process.env.LLM_BASE_URL) {
    console.log('[TIP] 若未配置模型渠道，Agent 对话会报模型错误：先到控制台「设置 → 模型」添加渠道并测试；');
    console.log('      或用环境变量 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 启动底座。');
  }
  console.log(`[INFO] 进程由调用方管理，本脚本不 kill：停止用  ${stopHint(pid)}`);
  process.exit(0);
}

console.error(`[FAIL] 启动超时（${timeoutSec}s）：${base} 健康检查仍未通过（${health.detail}）`);
console.error(`       进程可能仍在启动中：PID ${pid}，日志 ${logPath}`);
console.error(`       继续观察：${process.platform === 'win32' ? `Get-Content -Tail 80 -Wait '${logPath}'` : `tail -f ${logPath}`}`);
console.error('       ----- 日志末尾 -----');
console.error(readLogTail(logPath, 40));
console.error(`       确认放弃时结束进程：${stopHint(pid)}`);
process.exit(1);
