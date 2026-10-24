#!/usr/bin/env node
/**
 * 环境自检：Node / Java 17+ / Maven 版本与可用性，外加底座与应用默认端口占用。
 *
 * 用法：
 *   node check_env.mjs [--harness <jnf-harness 目录>] [--port 8090] [--app-port 18081]
 *
 * 退出码：0 = 关键项全部通过（端口被占用只提示，不算失败）；1 = 缺 Node/Java 等关键依赖。
 * 零 npm 依赖（只用 Node 内置模块），Windows / macOS / Linux 通用。
 */
import { spawnSync } from 'node:child_process';
import { createConnection } from 'node:net';
import { existsSync } from 'node:fs';
import path from 'node:path';

const HELP = `环境自检（Node / Java / Maven / 端口）

用法:
  node check_env.mjs [选项]

选项:
  --harness <dir>    jnf-harness 仓库目录（也可用环境变量 JNF_HARNESS_DIR），用于检查底座 jar
  --port <n>         底座端口，默认 8090
  --app-port <n>     业务应用端口，默认 18081
  --help, -h         显示本帮助

示例:
  node check_env.mjs --harness jnf-harness
  node check_env.mjs --port 18094 --app-port 18083

说明:
  - Java 需要 17+（底座与插件都用 Java 17 编译）；Maven 只在构建插件/应用时才需要。
  - 端口被占用只提示：可能是服务已在运行（可复用），也可能是别的程序（换端口）。`;

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

/**
 * 版本探测：java 是 .exe 可直接 spawn；mvn 在 Windows 是 .cmd，必须经 cmd.exe 启动
 * （不走 shell:true，避免 Node 的 DEP0190 参数拼接告警）。
 */
function runVersion(cmd, args) {
  const options = { encoding: 'utf8', timeout: 20000, windowsHide: true };
  if (process.platform === 'win32' && cmd === 'mvn') {
    return spawnSync(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', [cmd, ...args].join(' ')], options);
  }
  return spawnSync(cmd, args, options);
}

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

const opts = parseArgs(process.argv.slice(2));
if (opts.help) {
  console.log(HELP);
  process.exit(0);
}

const port = Number(opts.port || 8090);
const appPort = Number(opts['app-port'] || 18081);
const harnessDir = path.resolve(
  typeof opts.harness === 'string' ? opts.harness : process.env.JNF_HARNESS_DIR || 'jnf-harness',
);

let failures = 0;
const ok = (msg) => console.log(`[OK]   ${msg}`);
const warn = (msg) => console.log(`[WARN] ${msg}`);
const fail = (msg) => {
  failures++;
  console.log(`[FAIL] ${msg}`);
};
const tip = (msg) => console.log(`       → ${msg}`);

console.log('== 1/4 Node.js ==');
{
  const major = Number(process.version.replace(/^v/, '').split('.')[0]);
  if (major >= 20) {
    ok(`node ${process.version}`);
  } else if (major >= 18) {
    warn(`node ${process.version}：建议 20+（脚本用 fetch/ReadableStream，18 可用但不再维护）`);
    tip('安装 LTS：winget install OpenJS.NodeJS.LTS  或  https://nodejs.org/');
  } else {
    fail(`node ${process.version} 太旧，脚本要求 Node 18+（推荐 20+）`);
    tip('升级：winget install OpenJS.NodeJS.LTS');
  }
}

console.log('== 2/4 Java（需要 17+） ==');
{
  const res = runVersion('java', ['-version']);
  const text = `${res.stdout || ''}\n${res.stderr || ''}`.trim();
  if (res.error || !text) {
    fail('未找到可用的 java（PATH 里没有 java.exe？）');
    tip('Windows: winget install EclipseAdoptium.Temurin.17.JDK 然后重开终端');
    tip('验证：java -version  应输出 openjdk version "17.x" 或更高');
  } else {
    const firstLine = text.split(/\r?\n/)[0].trim();
    const m = text.match(/version\s+"?(\d+)(?:\.(\d+))?/);
    let major = m ? Number(m[1]) : NaN;
    if (major === 1 && m && m[2]) major = Number(m[2]); // 1.8.0 → 8
    if (Number.isFinite(major) && major >= 17) {
      ok(`java ${firstLine}`);
    } else {
      fail(`需要 JDK 17+，当前：${firstLine || text.split(/\r?\n/)[0]}`);
      tip('Windows: winget install EclipseAdoptium.Temurin.17.JDK');
    }
  }
}

console.log('== 3/4 Maven（构建应用/插件时需要） ==');
{
  const res = runVersion('mvn', ['-v']);
  const text = `${res.stdout || ''}\n${res.stderr || ''}`.trim();
  const line = text.split(/\r?\n/).find((l) => /Apache Maven/i.test(l));
  if (res.error || !line) {
    warn('未找到 mvn：只跑预构建 jar 时不需要，自己写应用/插件时需要');
    tip('Windows: winget install Apache.Maven（装完重开终端）；离线环境用 -o 走本地仓库');
  } else {
    const v = (line.match(/(\d+\.\d+(?:\.\d+)?)/) || [])[1] || 'unknown';
    ok(`${line.trim()}`);
    if (Number(v.split('.')[0]) < 3 || (Number(v.split('.')[0]) === 3 && Number(v.split('.')[1]) < 6)) {
      warn(`Maven ${v} 偏旧，建议 3.6+（本项目在 3.9 上验证）`);
    }
  }
}

console.log('== 4/4 端口占用（占用≠失败，可能服务已在跑） ==');
for (const [name, p] of [['底座 harness', port], ['业务应用', appPort]]) {
  if (!Number.isInteger(p) || p <= 0 || p > 65535) {
    warn(`${name}端口非法：${p}`);
    continue;
  }
  const occupied = await probePort(p);
  if (occupied) {
    ok(`${name}端口 ${p} 已有进程监听（可直接复用；要新起实例就换端口）`);
    tip(`确认是谁：${process.platform === 'win32' ? `Get-NetTCPConnection -LocalPort ${p} -State Listen` : `lsof -iTCP:${p} -sTCP:LISTEN`}`);
  } else {
    ok(`${name}端口 ${p} 空闲`);
  }
}

if (typeof opts.harness === 'string' || process.env.JNF_HARNESS_DIR) {
  console.log('== 附加：底座制品 ==');
  const jar = path.join(harnessDir, 'jnf-harness-app', 'target', 'jnf-harness-app.jar');
  if (existsSync(jar)) {
    ok(`已找到底座 jar：${jar}`);
    const pluginsDir = path.join(path.dirname(jar), 'plugins');
    if (existsSync(pluginsDir)) {
      ok(`插件目录存在：${pluginsDir}（启动时自动装载）`);
    } else {
      warn(`插件目录不存在：${pluginsDir}（mvn package 会生成；也可启动后放 jar 再 restart）`);
    }
  } else {
    warn(`未找到底座 jar：${jar}`);
    tip(`先构建：cd ${harnessDir} && mvn -o -q -DskipTests package`);
  }
}

console.log('');
if (failures === 0) {
  console.log(`[OK] 关键依赖检查通过（${failures} 项失败）。下一步：node start_harness.mjs --port ${port}`);
  process.exit(0);
} else {
  console.log(`[FAIL] ${failures} 项关键检查失败，按上面的建议修复后重跑。`);
  process.exit(1);
}
