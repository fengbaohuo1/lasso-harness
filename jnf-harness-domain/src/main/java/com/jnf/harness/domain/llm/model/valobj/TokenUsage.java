package com.jnf.harness.domain.llm.model.valobj;

/**
 * Token 用量（计量上报用，见修正项 R11）。
 * <p>P11-5：新增 {@code cachedTokens}（提示词缓存命中量）——各协议字段名不同，
 * 由适配器统一归一到此：OpenAI {@code prompt_tokens_details.cached_tokens}、
 * DeepSeek {@code prompt_cache_hit_tokens}、Anthropic {@code cache_read_input_tokens}。
 */
public record TokenUsage(int promptTokens, int completionTokens, int cachedTokens) {

    /** 兼容旧构造面（无缓存统计）。 */
    public TokenUsage(int promptTokens, int completionTokens) {
        this(promptTokens, completionTokens, 0);
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                cachedTokens + other.cachedTokens);
    }
}
