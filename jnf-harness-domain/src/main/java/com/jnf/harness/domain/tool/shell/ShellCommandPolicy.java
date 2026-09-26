package com.jnf.harness.domain.tool.shell;

import java.util.List;

/**
 * shell 命令策略（修正项 R2：命令不再裸执行）。
 * <ul>
 *   <li>blacklist 模式（默认）：命令分段（按 {@code ; | & } 切分）逐段检查，命中拦截模式即拒绝；</li>
 *   <li>whitelist 模式（allowedPrefixes 非空时启用）：每段首个词必须命中允许清单，再过黑名单。</li>
 * </ul>
 */
public record ShellCommandPolicy(boolean allowAll, List<String> allowedPrefixes,
                                 List<String> blockedPatterns) {

    /** 默认策略：黑名单模式（危险命令拦截，其余放行）。 */
    public static ShellCommandPolicy defaultPolicy() {
        return new ShellCommandPolicy(false, List.of(), List.of(
                "rm -rf /", "rm -rf /*", "mkfs", "dd if=", ":(){", "shutdown", "reboot",
                "halt", "> /dev/sd", "chmod -R 777 /", "chown -R", "curl | sh", "wget | sh",
                "history -c", "iptables -F", "userdel", "mkfs.ext", ":(){ :|:& };:"));
    }

    /** 白名单模式：只允许清单内命令（仍过黑名单）。 */
    public static ShellCommandPolicy whitelistOf(List<String> allowedPrefixes) {
        return new ShellCommandPolicy(false, allowedPrefixes, defaultPolicy().blockedPatterns());
    }

    /** 全放行（显式声明风险自担；仅测试/沙箱环境使用）。 */
    public static ShellCommandPolicy permissive() {
        return new ShellCommandPolicy(true, List.of(), List.of());
    }
}
