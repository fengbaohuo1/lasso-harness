#!/usr/bin/env node
/**
 * 安装并激活 jnf-harness Java Native 插件（幂等）：catalog 兜底 → install → activate/reload → contributions。
 *
 * 用法：
 *   node install_plugin.mjs --base http://127.0.0.1:8090 \
 *     [--api-key <key>] --jar <插件 jar 绝对路径> [--plugin-id <id>] \
 *     [--entrypoint <主类全名>] [--name <显示名>] [--version 1.0.0] [--reload]
 *
 * 不带 --jar 时按 --plugin-id 从插件目录 catalog 里找（适合「jar 已在 plugins/ 且启动自动装载」的场景）。
 * 退出码：0 = 已安装且 ACTIVE；1 = 任一步失败（会打印信封 code/info）。
 * 零 npm 依赖。
 */
import { existsSync } from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import https from 'node:https';

/** Node 内置 http/https 极简请求（不用 fetch：Node 24 Windows 在连接池仍有 handle 时强制 exit 会触发 libuv 断言）。 */
function httpRequest(url, { method = 'GET', headers = {}, body, timeoutMs = 20000 } = {}) {
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

const HELP = `安装并激活 jnf-harness Java Native 插件（幂等，可重复执行）

用法:
  node install_plugin.mjs [选项]
选项:
  --base <url>        底座地址，默认 http://127.0.0.1:8090
  --api-key <key>     底座启用鉴权时的 X-API-Key
  --jar <path>        插件 jar 路径（建议绝对路径；相对路径会转成绝对路径再提交）
  --plugin-id <id>    pluginId；不给则从 catalog 或 jar 文件名推断
  --entrypoint <类>   入口主类全名（plugin.yaml 的 entrypoint）；不给则用 catalog 中的值
  --name <显示名>     显示名，默认取 pluginId
  --version <版本>    pluginVersion，默认 1.0.0
  --reload            已 ACTIVE 时强制 reload（替换了同名 jar 后用）
  --timeout <秒>      单次 HTTP 超时，默认 20
  --help, -h          显示本帮助

示例:
  node install_plugin.mjs --base http://127.0.0.1:8090 --jar D:/demo/sample-plugin/target/sample-plugin-1.0.0.jar \\
    --plugin-id demo-bridge --entrypoint com.example.appplugin.plugin.DemoBridgePlugin --version 1.0.0
退出码: 0 = 插件 ACTIVE；1 = catalog/install/activate 失败。`;

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
const timeoutMs = Math.max(1000, Number(opts.timeout || 20) * 1000);
let wantPluginId = typeof opts['plugin-id'] === 'string' ? opts['plugin-id'].trim() : '';
let entrypoint = typeof opts.entrypoint === 'string' ? opts.entrypoint.trim() : '';
let displayName = typeof opts.name === 'string' ? opts.name.trim() : '';
let version = typeof opts.version === 'string' ? opts.version.trim() : '';

class HttpError extends Error {}
async function api(pathname, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (apiKey) headers['X-API-Key'] = apiKey;
  let res;
  try {
    res = await httpRequest(base + pathname, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      timeoutMs,
    });
  } catch (error) {
    throw new HttpError(`请求 ${method} ${pathname} 失败：${error?.message || error}（底座是否在 ${base} 运行？）`);
  }
  if (res.status === 401) {
    throw new HttpError('底座拒绝了请求（HTTP 401）：需要 API Key，请加 --api-key <key>');
  }
  let json = null;
  try {
    json = JSON.parse(res.text);
  } catch {
    /* 非 JSON 保留原文 */
  }
  return { status: res.status, json, text: res.text };
}

function assertEnvelope(result, what) {
  const code = result.json?.code;
  if (result.status !== 200 || code !== '00000') {
    const info = result.json?.info || result.text?.slice(0, 300) || '(空响应)';
    const detail = `code=${code ?? `HTTP ${result.status}`}`;
    throw new HttpError(`${what}失败：${detail}，info=${info}${hintFor(info)}`);
  }
  return result.json.data;
}

const HINTS = [
  ['制品不存在', 'jar 路径写错或已被移动；确认 --jar 指向真实存在的 jar（建议绝对路径）'],
  ['未找到插件入口', '清单漏了 entrypoint 且 jar 内无 META-INF/services/com.jnf.harness.spi.JavaHarnessPlugin；补 --entrypoint 后重跑'],
  ['pluginId 不一致', 'plugin.yaml 的 id 与入口类 pluginId() 不一致；改一致后重新 package'],
  ['需要 SDK', '插件 sdkVersion 高于宿主；改成 sdkVersion: 1 重新打包'],
];
function hintFor(info) {
  const text = String(info || '');
  const hit = HINTS.find(([key]) => text.includes(key));
  return hit ? `\n       → ${hit[1]}` : '';
}

function sameArtifact(recorded, absolute) {
  if (!recorded) return false;
  const recordedName = path.basename(recorded).toLowerCase();
  if (recordedName !== path.basename(absolute).toLowerCase()) return false;
  return path.isAbsolute(recorded)
    ? path.resolve(recorded).toLowerCase() === path.resolve(absolute).toLowerCase()
    : true;
}

/** 从 jar 文件名推断 pluginId：sample-plugin-1.0.0.jar → sample-plugin。 */
function inferPluginId(jarPath) {
  const name = path.basename(jarPath).replace(/\.jar$/i, '');
  return name.replace(/-\d+(?:\.\d+)*(?:[-.][A-Za-z0-9]+)?$/, '') || name;
}

console.log(`[INFO] 底座: ${base}`);
let plugins = [];
try {
  plugins = (assertEnvelope(await api('/api/harness/plugins'), '读取插件列表') || []);
} catch (error) {
  console.error(`[FAIL] ${error.message}`);
  process.exit(1);
}

let jarPath = '';
if (typeof opts.jar === 'string') {
  jarPath = path.resolve(opts.jar);
  if (!existsSync(jarPath)) {
    console.error(`[FAIL] 插件 jar 不存在: ${jarPath}`);
    console.error('       先构建：mvn -o -q -DskipTests package（产物在 <plugin>/target/*.jar）');
    process.exit(1);
  }
}

// ---- 1. 推断/回填 pluginId、entrypoint、显示名、版本 ----
const catalogData = (await api('/api/harness/plugins/catalog').then((r) => r.json?.data || []).catch(() => [])) || [];
let catalogHit = null;
if (jarPath) {
  catalogHit = catalogData.find((m) => sameArtifact(m.sourcePath, jarPath)) || null;
} else if (wantPluginId) {
  catalogHit = catalogData.find((m) => m.pluginId === wantPluginId) || null;
}

if (!wantPluginId) {
  wantPluginId = catalogHit?.pluginId || (jarPath ? inferPluginId(jarPath) : '');
}
if (!wantPluginId) {
  console.error('[FAIL] 无法确定 pluginId：请传 --plugin-id 或 --jar');
  process.exit(1);
}
if (!displayName) displayName = catalogHit?.displayName || wantPluginId;
if (!version) version = catalogHit?.version || '1.0.0';
if (!entrypoint) entrypoint = catalogHit?.entrypoint || '';

const existing = plugins.find((p) => p.pluginId === wantPluginId) || null;
if (!jarPath) {
  if (!existing) {
    console.error(`[FAIL] --jar 未指定，且目录 catalog 里没有 ${wantPluginId}`);
    console.error('       请传 --jar <插件 jar 路径>，或确认 jar 已放进底座 jar 同级的 plugins/ 目录');
    process.exit(1);
  }
  jarPath = catalogHit?.sourcePath || existing.sourcePath || '';
  console.log(`[INFO] 复用已登记制品: ${jarPath}`);
}
if (!entrypoint) {
  console.log('[WARN] 未提供 --entrypoint：宿主将回退 META-INF/services 或 UI 清单查找入口');
}

// ---- 2. 登记（幂等 upsert） ----
console.log(`[1/3] install  pluginId=${wantPluginId} version=${version}`);
console.log(`      sourcePath=${jarPath}`);
if (entrypoint) console.log(`      entrypoint=${entrypoint}`);
try {
  const record = assertEnvelope(
    await api('/api/harness/plugins/install', {
      method: 'POST',
      body: {
        pluginId: wantPluginId,
        displayName,
        pluginVersion: version,
        runtimeType: 'JAVA_NATIVE',
        sourcePath: jarPath,
        entrypoint,
      },
    }),
    '登记插件',
  );
  console.log(`      OK 记录已写入（status=${record?.status ?? 'REGISTERED'}）`);
} catch (error) {
  console.error(`[FAIL] ${error.message}`);
  process.exit(1);
}

// ---- 3. 激活：install 会把记录状态重置为 REGISTERED，所以总是再 activate（服务端对已激活幂等）；
// 制品路径变了或显式 --reload 时走 reload（重建类加载器，才会加载新 jar） ----
const artifactChanged = existing ? !sameArtifact(existing.sourcePath, jarPath) : false;
try {
  if (artifactChanged || opts.reload) {
    console.log('[2/3] reload    制品有变化或指定 --reload → 停用后按新制品重新激活');
    assertEnvelope(await api(`/api/harness/plugins/${encodeURIComponent(wantPluginId)}/reload`, { method: 'POST' }), '热重载插件');
    console.log('      OK 已重新加载');
  } else {
    console.log('[2/3] activate（幂等：已激活时宿主直接返回，不改动运行时）');
    assertEnvelope(
      await api('/api/harness/plugins/activate', { method: 'POST', body: { pluginId: wantPluginId } }),
      '激活插件',
    );
    console.log('      OK 已激活');
  }
} catch (error) {
  console.error(`[FAIL] ${error.message}`);
  console.error('       排错：GET /api/harness/plugins 看 status/runtimeState；看底座日志里的插件加载异常栈');
  process.exit(1);
}

// ---- 4. 贡献面 ----
console.log('[3/3] contributions');
let contributions;
try {
  contributions = assertEnvelope(
    await api(`/api/harness/plugins/${encodeURIComponent(wantPluginId)}/contributions`),
    '读取插件贡献',
  );
} catch (error) {
  console.error(`[FAIL] ${error.message}`);
  process.exit(1);
}

const tools = contributions?.tools || [];
const endpoints = contributions?.endpoints || [];
if (tools.length === 0 && endpoints.length === 0) {
  console.error('[FAIL] 插件 ACTIVE 但没有任何工具/端点贡献：检查 configure() 是否调了 super.configure(context) 并注册工具');
  process.exit(1);
}
console.log(`      runtimeState=${contributions?.runtimeState ?? 'UNKNOWN'}`);
console.log(`      工具（${tools.length}）:`);
for (const tool of tools) {
  const qualified = tool.name?.startsWith('plugin__') ? tool.name : `plugin__${wantPluginId}__${tool.name}`;
  console.log(`        - ${qualified}${tool.description ? `  ${String(tool.description).slice(0, 80)}` : ''}`);
}
console.log(`      端点（${endpoints.length}）:`);
for (const endpoint of endpoints) {
  console.log(`        - ${endpoint.method} /api/harness/plugins/${wantPluginId}/api/${endpoint.path}`);
}
console.log('');
console.log(`[OK] 插件 ${wantPluginId} 已 ACTIVE。验证：`);
console.log(`     curl ${base}/api/harness/plugins`);
console.log(`     curl ${base}/api/harness/plugins/${wantPluginId}/contributions`);
console.log(`     让 Agent 实测：node agent_stream.mjs --base ${base} --agent app-e2e --message "调用工具列出应用数据"`);
