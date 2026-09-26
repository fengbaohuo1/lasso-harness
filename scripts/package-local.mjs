#!/usr/bin/env node
/**
 * 本地打包：mvn 产物 + 启动脚本 → dist/ zip（免 Docker 部署包）。
 * 用法：node scripts/package-local.mjs
 */
import { execSync } from 'node:child_process';
import { mkdirSync, copyFileSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const jar = path.join(root, 'jnf-harness-app', 'target', 'jnf-harness-app.jar');
const dist = path.join(root, 'dist', 'jnf-harness-local');

console.log('[package] 构建后端 JAR …');
execSync('mvn -q -B clean package -DskipTests', { cwd: root, stdio: 'inherit' });

if (!existsSync(jar)) {
  console.error('[package] JAR 未产出: ' + jar);
  process.exit(1);
}

rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });
copyFileSync(jar, path.join(dist, 'jnf-harness-app.jar'));

writeFileSync(path.join(dist, 'start.sh'), [
  '#!/usr/bin/env bash',
  'cd "$(dirname "$0")"',
  'exec java -jar jnf-harness-app.jar --spring.profiles.active=standalone "$@"',
  '',
].join('\n'), { mode: 0o755 });

writeFileSync(path.join(dist, 'start.bat'), [
  '@echo off',
  'cd /d "%~dp0"',
  'java -jar jnf-harness-app.jar --spring.profiles.active=standalone %*',
  '',
].join('\r\n'));

console.log('[package] 完成: ' + dist);
console.log('[package] 运行: cd dist/jnf-harness-local && ./start.sh');
