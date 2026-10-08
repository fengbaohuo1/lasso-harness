package com.jnf.harness.domain.tool.shell;

import com.jnf.harness.spi.host.ShellService;

import java.nio.file.Path;

/**
 * {@link ShellService} 宿主实现：命令守卫（R2）内建 + 执行器委托（P1 宿主服务）。
 * <p>shell 工具已外移为官方插件（jnf-harness-plugin-shell），经
 * {@code PluginContext.lookupService(ShellService.class)} 取得本服务；
 * 治理（守卫/超时/平台适配）集中在宿主侧，插件不重复实现。
 */
public class ShellHostService implements ShellService {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final IShellExecutor executor;
    private final ShellCommandGuard guard;
    private final Path defaultCwd;

    public ShellHostService(IShellExecutor executor, ShellCommandGuard guard, Path defaultCwd) {
        this.executor = executor;
        this.guard = guard;
        this.defaultCwd = defaultCwd;
    }

    @Override
    public ShellOutcome execute(String command, String cwd, long timeoutSeconds) {
        ShellCommandGuard.Decision decision = guard.evaluate(command == null ? "" : command);
        if (!decision.allowed()) {
            return ShellOutcome.denied(decision.reason());
        }
        try {
            Path workDir = (cwd == null || cwd.isBlank()) ? defaultCwd : Path.of(cwd);
            workDir = workDir.toAbsolutePath().normalize();
            long timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
            ShellResult result = executor.execute(command, workDir, timeout);
            return ShellOutcome.ok(result.exitCode(), result.output(), result.timedOut());
        } catch (Exception e) {
            return new ShellOutcome(-1, "执行失败: " + e.getMessage(), false, false, null);
        }
    }
}
