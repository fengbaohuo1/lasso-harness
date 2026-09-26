package com.jnf.harness.domain.tool.shell;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * shell 命令执行（修正项 R2：默认策略下先过 {@link ShellCommandGuard}，不裸执行）。
 */
public class ShellExecuteTool extends AbstractTool {

    private final IShellExecutor executor;
    private final ShellCommandGuard guard;

    public ShellExecuteTool(IShellExecutor executor, ShellCommandGuard guard) {
        this.executor = executor;
        this.guard = guard;
    }

    @Override
    public String name() {
        return "shell_execute";
    }

    @Override
    public String description() {
        return "在工作目录执行 shell 命令并返回 stdout/stderr 与退出码。危险命令会被策略拦截。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("command", stringSchema("要执行的命令"))
                .prop("timeout_seconds", stringSchema("超时秒数（可选，默认 60）"))
                .required("command")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String command = str(args, "command");
        if (command.isBlank()) {
            return fail("command 不能为空", "MISSING_COMMAND");
        }
        ShellCommandGuard.Decision decision = guard.evaluate(command);
        if (!decision.allowed()) {
            return fail("命令被安全策略拦截：" + decision.reason(), "COMMAND_BLOCKED");
        }
        long timeout = 60;
        try {
            String raw = str(args, "timeout_seconds");
            if (!raw.isBlank()) {
                timeout = Long.parseLong(raw.trim());
            }
        } catch (NumberFormatException ignored) {
            // 用默认值
        }
        try {
            Path cwd = Path.of(context.cwd()).toAbsolutePath().normalize();
            ShellResult result = executor.execute(command, cwd, timeout);
            String output = ("exit=" + result.exitCode() + (result.timedOut() ? "（超时被终止）" : "")
                    + "\n" + truncate(result.output()));
            // 非零退出码也是模型可用的正常结果（不是工具失败）
            return ok(output);
        } catch (Exception e) {
            return fail("执行失败: " + e.getMessage(), "SHELL_ERROR");
        }
    }

    private String truncate(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > 32_000 ? output.substring(0, 32_000) + "\n...（输出截断）" : output;
    }
}
