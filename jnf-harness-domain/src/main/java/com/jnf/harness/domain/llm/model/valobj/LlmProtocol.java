package com.jnf.harness.domain.llm.model.valobj;

/**
 * 模型协议（决定用哪个适配器）。
 */
public enum LlmProtocol {
    OPENAI,
    ANTHROPIC,
    OLLAMA;

    public static LlmProtocol from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPENAI;
        }
        return switch (raw.trim().toLowerCase()) {
            case "anthropic" -> ANTHROPIC;
            case "ollama" -> OLLAMA;
            default -> OPENAI;
        };
    }
}
