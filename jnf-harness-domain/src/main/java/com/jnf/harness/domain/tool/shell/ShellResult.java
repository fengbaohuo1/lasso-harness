package com.jnf.harness.domain.tool.shell;

/**
 * shell 执行结果。
 */
public record ShellResult(int exitCode, String output, boolean timedOut) {
}
