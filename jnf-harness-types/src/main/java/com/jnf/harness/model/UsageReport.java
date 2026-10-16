package com.jnf.harness.model;

/**
 * 用量上报（P11-5）：会话累计用量 + 最近一次 LLM 调用的耗时与速度。
 * <p>放在 types 契约层是因为它要横跨 domain（采集）与 api（SSE 缝）两侧。
 * <p>放在这里而不是每处各拼一个 Map，是为了让「缓存命中率 / 输出速度」的口径只有一处。
 */
public record UsageReport(long promptTokens, long completionTokens, long cachedTokens,
                          long llmCalls, long lastElapsedMs, double tokensPerSecond,
                          long lastPromptTokens) {

    public static UsageReport empty() {
        return new UsageReport(0, 0, 0, 0, 0, 0, 0);
    }

    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    /** 缓存命中率（命中量 / 提示词总量）；无提示词时为 0。 */
    public double cacheHitRate() {
        return promptTokens <= 0 ? 0d : (double) cachedTokens / (double) promptTokens;
    }
}
