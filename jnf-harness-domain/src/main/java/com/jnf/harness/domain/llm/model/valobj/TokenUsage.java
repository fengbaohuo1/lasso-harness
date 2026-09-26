package com.jnf.harness.domain.llm.model.valobj;

/**
 * Token 用量（计量上报用，见修正项 R11）。
 */
public record TokenUsage(int promptTokens, int completionTokens) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
    }
}
