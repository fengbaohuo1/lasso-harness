#!/usr/bin/env node
/**
 * 冒烟验证：底座健康检查 + 插件列表；可选校验指定插件 ACTIVE 与贡献面。
 *
 * 用法：
 *   node smoke_test.mjs [--base http://127.0.0.1:8090] [--api-key <key>] [--plugin <id>] [--timeout 10]
 *
 * 输出逐项 PASS/FAIL 与汇总；任一 FAIL 退出码 1。
 * 零 npm 依赖。
 */
import http from 'node:http';
import https from 'node:https';

/** Node 内置 http/https 极简请求（不用 fetch：Node 24 Windows 在连接池仍有 handle 时强制 exit 会触发 libuv 断言）。 */
function httpRequest(url, { method = 'GET', headers = {}, body, timeoutMs = 10000 } = {}) {
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
    req.setTimeout(timeoutMs, () => req.destroy(new Error(`请求超时（${timeoutMs}ms）：${url}`)));
    req.on('error', reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}

const HELP = `底座冒烟验证（健康检查 + 插件列表 + 可选插件 ACTIVE 校验）

用法:
  node smoke_test.mjs [选项]

选项:
  --base <url>      底座地址，默认 http://127.0.0.1:8090
  --api-key <key>   底座启用鉴权时的 X-API-Key
  --plugin <id>     额外校验该插件：status=ACTIVE 且 /contributions 能读到工具或端点
  --timeout <秒>    单次请求超时，默认 10
  --help, -h        显示本帮助

示例:
  node smoke_test.mjs --base http://127.0.0.1:8090
  node smoke_test.mjs --base http://127.0.0.1:8090 --plugin demo-bridge

退出码: 0 = 全部 PASS；1 = 有 FAIL 项。`;

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
const pluginId = typeof opts.plugin === 'string' ? opts.plugin : '';
const timeoutMs = Math.max(1000, Number(opts.timeout || 10) * 1000);

let passes = 0;
let fails = 0;
function pass(msg) {
  passes++;
  console.log(`  PASS  ${msg}`);
}
function fail(msg) {
  fails++;
  console.log(`  FAIL  ${msg}`);
}

async function request(pathname, { method = 'GET' } = {}) {
  const headers = { Accept: 'application/json' };
  if (apiKey) headers['X-API-Key'] = apiKey;
  const res = await httpRequest(base + pathname, { method, headers, timeoutMs });
  let json = null;
  try {
    json = JSON.parse(res.text);
  } catch {
    /* 保留原文 */
  }
  return { status: res.status, json, text: res.text };
}

async function checkHealth() {
  console.log('== 1/3 底座健康检查 ==');
  try {
    const res = await request('/actuator/health');
    if (res.status === 200) {
      let status = '?';
      try {
        status = JSON.parse(res.text).status;
      } catch {
        /* ignore */
      }
      if (status === 'UP') {
        pass(`/actuator/health status=UP  (${base})`);
      } else {
        fail(`/actuator/health 返回 status=${status}，期望 UP：底座可能还在启动或依赖异常`);
      }
      return true;
    }
    // actuator 不可用时的兜底
    const api = await request('/api/harness/plugins');
    if (api.status === 200 && api.json?.code === '00000') {
      pass(`/actuator/health HTTP ${res.status}，但业务接口可用（code=00000）`);
      return true;
    }
    fail(`健康检查 HTTP ${res.status}，且业务接口不可用：确认端口/进程`);
    return false;
  } catch (error) {
    fail(`探活失败：${error?.message || error}（底座是否已启动？node start_harness.mjs）`);
    return false;
  }
}

async function checkPluginList() {
  console.log('== 2/3 插件列表 ==');
  try {
    const res = await request('/api/harness/plugins');
    if (res.status === 401) {
      fail('HTTP 401：需要 API Key，请加 --api-key <key>');
      return [];
    }
    if (res.status !== 200 || res.json?.code !== '00000') {
      fail(`GET /api/harness/plugins code=${res.json?.code ?? `HTTP ${res.status}`} info=${res.json?.info ?? res.text.slice(0, 120)}`);
      return [];
    }
    const plugins = res.json.data || [];
    pass(`插件接口正常（code=00000，共 ${plugins.length} 个）`);
    for (const plugin of plugins) {
      console.log(`        - ${plugin.pluginId}  status=${plugin.status}  runtime=${plugin.runtimeState}  version=${plugin.pluginVersion || '-'}`);
    }
    return plugins;
  } catch (error) {
    fail(`插件列表请求失败：${error?.message || error}`);
    return [];
  }
}

async function checkPlugin(plugins) {
  console.log(`== 3/3 指定插件校验${pluginId ? `: ${pluginId}` : '（未指定 --plugin，跳过）'} ==`);
  if (!pluginId) {
    console.log('  SKIP  未传 --plugin');
    return;
  }
  const record = plugins.find((p) => p.pluginId === pluginId);
  if (!record) {
    fail(`插件未安装: ${pluginId}（先 node install_plugin.mjs --jar <jar> --plugin-id ${pluginId}）`);
    return;
  }
  const alive = record.status === 'ACTIVE' || record.runtimeState === 'ACTIVE';
  if (!alive) {
    fail(`插件状态 ${record.status}/${record.runtimeState}，期望 ACTIVE（重新 install/activate）`);
  } else {
    pass(`插件 ${pluginId} 状态 ACTIVE（status=${record.status}, runtime=${record.runtimeState}）`);
  }
  try {
    const res = await request(`/api/harness/plugins/${encodeURIComponent(pluginId)}/contributions`);
    if (res.status !== 200 || res.json?.code !== '00000') {
      fail(`读取 contributions 失败：code=${res.json?.code ?? `HTTP ${res.status}`} info=${res.json?.info ?? ''}`);
      return;
    }
    const data = res.json.data || {};
    const tools = data.tools || [];
    const endpoints = data.endpoints || [];
    if (tools.length === 0 && endpoints.length === 0) {
      fail(`插件 ${pluginId} ACTIVE 但无工具/端点贡献（检查 configure() 是否注册）`);
      return;
    }
    pass(`贡献面：工具 ${tools.length} 个 / 端点 ${endpoints.length} 个`);
    for (const tool of tools) console.log(`        - 工具 ${tool.name}`);
    for (const endpoint of endpoints) console.log(`        - 端点 ${endpoint.method} /api/harness/plugins/${pluginId}/api/${endpoint.path}`);
  } catch (error) {
    fail(`contributions 请求失败：${error?.message || error}`);
  }
}

console.log(`[INFO] 冒烟目标: ${base}${pluginId ? `（校验插件 ${pluginId}）` : ''}`);
const healthOk = await checkHealth();
const plugins = await checkPluginList();
if (healthOk || plugins.length > 0) {
  await checkPlugin(plugins);
}

console.log('');
console.log('== 结果 ==');
console.log(`PASS ${passes} / FAIL ${fails}`);
if (fails === 0) {
  console.log('[OK] 冒烟通过。');
  process.exit(0);
} else {
  console.log('[FAIL] 有失败项，修复后重跑。');
  if (!healthOk) console.log('       底座没起来：node start_harness.mjs --harness <jnf-harness 目录> --port <端口>');
  process.exit(1);
}
