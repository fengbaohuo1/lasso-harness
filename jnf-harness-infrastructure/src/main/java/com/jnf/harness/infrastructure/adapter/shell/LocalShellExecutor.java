package com.jnf.harness.infrastructure.adapter.shell;

import com.jnf.harness.domain.tool.shell.IShellExecutor;
import com.jnf.harness.domain.tool.shell.ShellResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地 shell 执行器：ProcessBuilder 直启（不经 shell 拼接注入面之外的路径）。
 */
public class LocalShellExecutor implements IShellExecutor {

    @Override
    public ShellResult execute(String command, Path cwd, long timeoutSeconds) {
        List<String> cmdline = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            cmdline.add("cmd");
            cmdline.add("/c");
            // cmd 内置命令输出编码在 cmd.exe 启动时固化（chcp 对同进程无效，实测证实）：
            // 外层 chcp 65001 设码页，内层新 cmd 执行用户命令 → 输出 UTF-8，与解码一致
            cmdline.add("chcp 65001 >nul & cmd /c " + command);
        } else {
            cmdline.add("/bin/sh");
            cmdline.add("-c");
            cmdline.add(command);
        }
        try {
            Process process = new ProcessBuilder(cmdline)
                    .directory(cwd == null ? null : cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ShellResult(-1, output.toString(), true);
            }
            return new ShellResult(process.exitValue(), output.toString(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ShellResult(-1, "执行被中断", true);
        } catch (Exception e) {
            return new ShellResult(-1, "执行器异常: " + e.getMessage(), false);
        }
    }
}
