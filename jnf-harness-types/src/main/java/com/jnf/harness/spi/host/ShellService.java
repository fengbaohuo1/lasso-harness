package com.jnf.harness.spi.host;

/**
 * 宿主服务：受治理的 shell 执行通道（P1 宿主服务查询扩展点）。
 * <p>工具插件经 {@code PluginContext.lookupService(ShellService.class)} 获取；
 * 命令守卫（R2 白/黑名单）、超时与平台适配由宿主内建，插件不重复实现治理。
 */
public interface ShellService {

    /**
     * 执行命令。
     *
     * @param command        shell 命令
     * @param cwd            工作目录（空串=宿主默认工作区）
     * @param timeoutSeconds 超时秒数（&le;0 用宿主默认值）
     */
    ShellOutcome execute(String command, String cwd, long timeoutSeconds);

    /**
     * 执行结果：{@code denied=true} 时命令被守卫拒绝（内容为拒绝原因）。
     */
    record ShellOutcome(int exitCode, String output, boolean timedOut, boolean denied, String reason) {

        public static ShellOutcome ok(int exitCode, String output, boolean timedOut) {
            return new ShellOutcome(exitCode, output, timedOut, false, null);
        }

        public static ShellOutcome denied(String reason) {
            return new ShellOutcome(-1, "", false, true, reason);
        }
    }
}
