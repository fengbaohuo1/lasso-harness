#!/usr/bin/env node
/**
 * 交付检查：底座健康 → 插件列表 → 指定插件 ACTIVE → 应用地址探活（可选）。
 *
 * 用法：
 *   node delivery_check.mjs --plugin <pluginId> [--base http://127.0.0.1:8090] \
 *     [--api-key <key>] [--app-url http://127.0.0.1:18081] [--timeout 10]
 *
 * 逐项打印 PASS/FAIL，任一 FAIL 退出码 1。
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

const HELP = `交付前自动化检查（health / 插件列表 / 指定插件 ACTIVE / 应用探活）

用法:
  node delivery_check.mjs --plugin <pluginId> [选项]

选项:
  --plugin <id>      必填：本次交付的插件 pluginId
  --base <url>       底座地址，默认 http://127.0.0.1:8090
  --api-key <key>    底座启用鉴权时的 X-API-Key
  --app-url <url>    业务应用地址（可选）；给了才探活，默认不检查
  --timeout <秒>     单次请求超时，默认 10
  --help, -h         显示本帮助

示例:
  node delivery_check.mjs --plugin demo-bridge --app-url http://127.0.0.1:18081

退出码: 0 = 全部通过；1 = 有失败项（逐条给出修复建议）。`;

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
const appUrl = typeof opts['app-url'] === 'string' ? opts['app-url'].replace(/\/+$/, '') : '';
const timeoutMs = Math.max(1000, Number(opts.timeout || 10) * 1000);

if (!pluginId) {
  console.error('[FAIL] 缺少 --plugin <pluginId>（本次交付的插件 id）');
  console.error('       例：node delivery_check.mjs --plugin demo-bridge --app-url http://127.0.0.1:18081');
  process.exit(1);
}

let passes = 0;
let fails = 0;
const pass = (msg, note = '') => {
  passes++;
  console.log(`  PASS  ${msg}${note ? `  ${note}` : ''}`);
};
const fail = (msg, hint = '') => {
  fails++;
  console.log(`  FAIL  ${msg}`);
  if (hint) console.log(`        → ${hint}`);
};

async function request(pathname, options = {}) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (apiKey) headers['X-API-Key'] = apiKey;
  const res = await httpRequest(base + pathname, { method: options.method || 'GET', headers, timeoutMs });
  let json = null;
  try {
    json = JSON.parse(res.text);
  } catch {
    /* 保留原文 */
  }
  return { status: res.status, json, text: res.text };
}

console.log(`[INFO] 交付检查: 底座 ${base} / 插件 ${pluginId}${appUrl ? ` / 应用 ${appUrl}` : ''}`);

// 1. 底座健康
console.log('== 1/4 底座健康 ==');
let plugins = [];
try {
  const res = await request('/actuator/health');
  let status = '';
  try {
    status = JSON.parse(res.text).status;
  } catch {
    /* ignore */
  }
  if (res.status === 200 && status === 'UP') {
    pass('/actuator/health status=UP');
  } else {
    fail(`健康检查异常（HTTP ${res.status}, status=${status || '?'}）`, `node start_harness.mjs --harness <目录> --port <端口>，再重跑`);
  }
} catch (error) {
  fail(`探活失败：${error?.message || error}`, '底座没启动或端口不对；先 node smoke_test.mjs 排查');
}

// 2. 插件列表
console.log('== 2/4 插件列表 ==');
try {
  const res = await request('/api/harness/plugins');
  if (res.status === 401) {
    fail('HTTP 401：需要 API Key', '加 --api-key <key> 后重跑');
  } else if (res.status !== 200 || res.json?.code !== '00000') {
    fail(`接口异常 code=${res.json?.code ?? `HTTP ${res.status}`}`, res.json?.info || res.text.slice(0, 160));
  } else {
    plugins = res.json.data || [];
    pass(`GET /api/harness/plugins 正常（${plugins.length} 个已安装）`);
    for (const plugin of plugins) {
      console.log(`        - ${plugin.pluginId}  status=${plugin.status}  runtime=${plugin.runtimeState}`);
    }
  }
} catch (error) {
  fail(`插件列表请求失败：${error?.message || error}`, '底座未响应；检查端口与进程');
}

// 3. 指定插件 ACTIVE + 贡献面
console.log(`== 3/4 插件状态 (${pluginId}) ==`);
const record = plugins.find((p) => p.pluginId === pluginId);
if (!record) {
  fail(`插件未安装: ${pluginId}`, `node install_plugin.mjs --base ${base} --jar <插件 jar> --plugin-id ${pluginId}`);
} else if (record.status !== 'ACTIVE' && record.runtimeState !== 'ACTIVE') {
  fail(`插件状态 ${record.status}/${record.runtimeState}，期望 ACTIVE`, `POST /api/harness/plugins/activate {"pluginId":"${pluginId}"} 或重新 install_plugin`);
} else {
  pass(`插件 ${pluginId} ACTIVE（status=${record.status}, runtime=${record.runtimeState}）`);
  try {
    const res = await request(`/api/harness/plugins/${encodeURIComponent(pluginId)}/contributions`);
    const data = res.json?.data || {};
    const tools = data.tools || [];
    const endpoints = data.endpoints || [];
    if (res.status === 200 && res.json?.code === '00000' && (tools.length > 0 || endpoints.length > 0)) {
      pass(`贡献面：工具 ${tools.length} 个 / 端点 ${endpoints.length} 个`, tools.map((t) => t.name).join(', '));
    } else {
      fail('插件 ACTIVE 但没有可用的工具/端点贡献', '检查 configure() 是否 super.configure(context) 并注册工具');
    }
  } catch (error) {
    fail(`contributions 请求失败：${error?.message || error}`, '看底座日志里的插件加载异常');
  }
}

// 4. 应用探活（可选）
console.log('== 4/4 应用探活 ==');
if (!appUrl) {
  console.log('  SKIP  未传 --app-url（应用地址需人工探活后写进交付说明）');
} else {
  try {
    const res = await httpRequest(appUrl + '/', { timeoutMs });
    if (res.status === 200) {
      pass(`应用可访问 HTTP 200  (${appUrl})`);
    } else {
      fail(`应用返回 HTTP ${res.status}`, '确认应用已启动：java -jar <app>.jar --server.port=<端口>');
    }
  } catch (error) {
    fail(`应用探活失败：${error?.message || error}`, `确认 ${appUrl} 对应进程在跑，且端口没写错`);
  }
}

console.log('');
console.log('== 结果 ==');
console.log(`PASS ${passes} / FAIL ${fails}`);
if (fails === 0) {
  console.log('[OK] 自动化检查全部通过。');
  console.log('     继续过 references/delivery-checklist.md 的手工项：AI 面板真浏览器验证、每个工具的自然语言实测、写操作联动回归。');
  process.exit(0);
} else {
  console.log('[FAIL] 有失败项，按上面的 → 提示修复后重跑。');
  process.exit(1);
}
