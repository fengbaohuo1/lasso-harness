#!/usr/bin/env node
/**
 * 本地打包：mvn 产物 + 启动脚本 → dist/jnf-harness-local（免 Docker 部署包）。
 * 多 jar 布局（P4）：
 *   jnf-harness-app.jar          内核（fat jar，不含插件）
 *   plugins/*.jar                官方插件 + 示例插件（动态加载，放入即用）
 *   plugin-sdk/jnf-harness-types.jar   插件 SDK（第三方插件开发用，provided 依赖）
 * 用法：node scripts/package-local.mjs
 */
import { execSync } from 'node:child_process';
import { copyFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const jar = path.join(root, 'jnf-harness-app', 'target', 'jnf-harness-app.jar');
const pluginsOut = path.join(root, 'jnf-harness-app', 'target', 'plugins');
const sdkJar = path.join(root, 'jnf-harness-types', 'target');
const sampleJar = path.join(root, 'jnf-harness-sample-plugin', 'target');
const dist = path.join(root, 'dist', 'jnf-harness-local');

console.log('[package] 构建后端 …');
execSync('mvn -q -B clean package -DskipTests', { cwd: root, stdio: 'inherit' });

if (!existsSync(jar)) {
  console.error('[package] JAR 未产出: ' + jar);
  process.exit(1);
}

rmSync(dist, { recursive: true, force: true });
mkdirSync(path.join(dist, 'plugins'), { recursive: true });
mkdirSync(path.join(dist, 'plugin-sdk'), { recursive: true });
copyFileSync(jar, path.join(dist, 'jnf-harness-app.jar'));

// 官方插件 jar → dist/plugins
for (const name of readdirSync(pluginsOut)) {
  if (name.endsWith('.jar')) {
    copyFileSync(path.join(pluginsOut, name), path.join(dist, 'plugins', name));
    console.log('[package] 插件: plugins/' + name);
  }
}
// 示例插件（hello-world 级插件 jar 样板）一并带上
for (const name of readdirSync(sampleJar)) {
  if (name.endsWith('.jar') && !name.endsWith('.jar.original')) {
    copyFileSync(path.join(sampleJar, name), path.join(dist, 'plugins', name));
    console.log('[package] 插件: plugins/' + name);
  }
}
// 插件 SDK
for (const name of readdirSync(sdkJar)) {
  if (name.startsWith('jnf-harness-types') && name.endsWith('.jar') && !name.endsWith('.jar.original')) {
    copyFileSync(path.join(sdkJar, name), path.join(dist, 'plugin-sdk', name));
    console.log('[package] SDK: plugin-sdk/' + name);
  }
}

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
console.log('[package] 插件目录 plugins/ 会被内核自动装载（放入/移除 jar 即启停，重启生效）');
