package com.jnf.harness.domain.sandbox;

/**
 * 沙箱模式（三档）。
 */
public enum SandboxMode {
    /** 只读：写类工具全部拒绝。 */
    READ_ONLY,
    /** 工作区写：允许写，但路径必须在工作区内。 */
    WORKSPACE_WRITE,
    /** 危险全开：显式声明风险自担。 */
    DANGER_FULL_ACCESS;

    public static SandboxMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return WORKSPACE_WRITE;
        }
        return switch (raw.trim().toUpperCase()) {
            case "READ_ONLY" -> READ_ONLY;
            case "DANGER_FULL_ACCESS" -> DANGER_FULL_ACCESS;
            default -> WORKSPACE_WRITE;
        };
    }
}
