package com.jnf.harness.domain.llm.model.valobj;

/**
 * 生成结束原因（跨协议归一）。
 */
public enum FinishReason {
    STOP,
    LENGTH,
    TOOL_CALLS,
    ERROR,
    UNKNOWN;

    /** 归一各厂商的 finish_reason 字符串。 */
    public static FinishReason from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return switch (raw.trim().toLowerCase()) {
            case "stop", "end_turn", "stop_sequence" -> STOP;
            case "length", "max_tokens" -> LENGTH;
            case "tool_calls", "tool_use", "function_call" -> TOOL_CALLS;
            case "error" -> ERROR;
            default -> UNKNOWN;
        };
    }
}
