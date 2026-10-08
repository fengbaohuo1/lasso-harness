package com.jnf.harness.plugin.shell;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.host.ShellService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * shell 命令执行（修正项 R2：命令守卫内建于宿主 {@link ShellService}，不裸执行）。
 * <p>官方插件工具（jnf-harness-plugin-shell）：治理在宿主侧，本工具只做参数处理与结果呈现。
 */
public class ShellExecuteTool extends AbstractTool {

    private final ShellService shell;

    /** @param shell 宿主服务；可空（宿主未提供时工具降级报错，不阻断插件激活） */
    public ShellExecuteTool(ShellService shell) {
        this.shell = shell;
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
        if (shell == null) {
            return fail("宿主未提供 ShellService（插件降级）", "NO_HOST_SERVICE");
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
        ShellService.ShellOutcome outcome = shell.execute(command, context.cwd(), timeout);
        if (outcome.denied()) {
            return fail("命令被安全策略拦截：" + outcome.reason(), "COMMAND_BLOCKED");
        }
        // 非零退出码也是模型可用的正常结果（不是工具失败）
        String output = "exit=" + outcome.exitCode() + (outcome.timedOut() ? "（超时被终止）" : "")
                + "\n" + truncate(outcome.output());
        return ok(output);
    }

    private String truncate(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > 32_000 ? output.substring(0, 32_000) + "\n...（输出截断）" : output;
    }
}
