package com.jnf.harness.spi;

/**
 * Hook 裁决结果：ALLOW 放行 / BLOCK 拦截（跳过执行）/ DENY 拒绝（语义上更重，同样跳过执行）。
 */
public record PluginHookResult(Decision decision, String reason) {

    public enum Decision {
        ALLOW, BLOCK, DENY
    }

    public PluginHookResult {
        decision = decision == null ? Decision.ALLOW : decision;
        reason = reason == null ? "" : reason;
    }

    public static PluginHookResult allow() {
        return new PluginHookResult(Decision.ALLOW, "");
    }

    public static PluginHookResult block(String reason) {
        return new PluginHookResult(Decision.BLOCK, reason);
    }

    public static PluginHookResult deny(String reason) {
        return new PluginHookResult(Decision.DENY, reason);
    }

    public boolean intercepted() {
        return decision == Decision.BLOCK || decision == Decision.DENY;
    }
}
