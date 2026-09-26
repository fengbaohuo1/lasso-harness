#!/usr/bin/env node
/**
 * 文档数字体检（R12 纪律）：README 不写死数字，数字一律由本脚本现算。
 * 用法：node scripts/stats.mjs
 */
import { execSync } from 'node:child_process';
import { readdirSync, statSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function countFiles(dir, ext) {
  let count = 0;
  if (!existsSync(dir)) return 0;
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) count += countFiles(full, ext);
    else if (entry.endsWith(ext)) count += 1;
  }
  return count;
}

const modules = readdirSync(root).filter((name) =>
  statSync(path.join(root, name)).isDirectory() && name.startsWith('jnf-harness-'));

const javaMain = modules.reduce((sum, m) =>
  sum + countFiles(path.join(root, m, 'src', 'main', 'java'), '.java'), 0);
const javaTest = modules.reduce((sum, m) =>
  sum + countFiles(path.join(root, m, 'src', 'test', 'java'), '.java'), 0);

let tests = '(运行 mvn verify 后可见)';
try {
  const output = execSync('mvn -q -B test', { cwd: root, encoding: 'utf8', stdio: 'pipe' });
  const match = output.match(/Tests run: (\d+)/);
  if (match) tests = match[1];
} catch {
  // 测试失败时也输出统计
}

console.log('jnf-harness 统计（现算，勿写死进文档）：');
console.log('  模块数:      ' + modules.length);
console.log('  主源码文件:  ' + javaMain);
console.log('  测试文件:    ' + javaTest);
console.log('  测试用例数:  ' + tests);
