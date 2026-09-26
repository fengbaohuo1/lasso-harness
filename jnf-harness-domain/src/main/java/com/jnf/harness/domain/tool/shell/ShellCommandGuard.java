package com.jnf.harness.domain.tool.shell;

import java.util.Locale;

/**
 * shell 命令守卫：按 {@link ShellCommandPolicy} 评估命令是否放行。
 */
public class ShellCommandGuard {

    /** 评估结论。 */
    public record Decision(boolean allowed, String reason) {
        public static Decision allow() {
            return new Decision(true, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    private final ShellCommandPolicy policy;

    public ShellCommandGuard(ShellCommandPolicy policy) {
        this.policy = policy == null ? ShellCommandPolicy.defaultPolicy() : policy;
    }

    public Decision evaluate(String command) {
        if (command == null || command.isBlank()) {
            return Decision.deny("空命令");
        }
        if (policy.allowAll()) {
            return Decision.allow();
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (String pattern : policy.blockedPatterns()) {
            if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                return Decision.deny("命中危险命令模式: " + pattern);
            }
        }
        if (!policy.allowedPrefixes().isEmpty()) {
            for (String segment : command.split("[;|&]+")) {
                String firstToken = segment.trim().split("\\s+")[0];
                boolean matched = policy.allowedPrefixes().stream()
                        .anyMatch(prefix -> firstToken.equalsIgnoreCase(prefix));
                if (!matched) {
                    return Decision.deny("命令不在白名单内: " + firstToken);
                }
            }
        }
        return Decision.allow();
    }
}
