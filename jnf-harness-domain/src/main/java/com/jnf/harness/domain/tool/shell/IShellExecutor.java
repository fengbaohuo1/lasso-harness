package com.jnf.harness.domain.tool.shell;

import java.nio.file.Path;

/**
 * shell 执行端口（infra 提供本地实现）。
 */
public interface IShellExecutor {

    ShellResult execute(String command, Path cwd, long timeoutSeconds);
}
