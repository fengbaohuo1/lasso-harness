package com.jnf.harness.domain.runtime.meter;

import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.model.UsageReport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话级 token 计量（修正项 R11：计量作为一等能力）。
 * <p>P11-5：补采缓存命中量与最近一次调用的耗时，用于算缓存命中率与输出速度。
 */
public class SessionTokenMeter {

    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong cachedTokens = new AtomicLong();
    private final AtomicLong llmCalls = new AtomicLong();
    private final AtomicLong lastElapsedMs = new AtomicLong();
    private final AtomicLong lastCompletionTokens = new AtomicLong();
    private final AtomicLong lastPromptTokens = new AtomicLong();

    public void record(TokenUsage usage) {
        record(usage, 0L);
    }

    /**
     * @param elapsedMs 本次 LLM 调用的墙钟耗时（0=未采集，速度按 0 计）
     */
    public void record(TokenUsage usage, long elapsedMs) {
        if (usage == null) {
            return;
        }
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
        cachedTokens.addAndGet(usage.cachedTokens());
        llmCalls.incrementAndGet();
        lastElapsedMs.set(Math.max(0L, elapsedMs));
        lastCompletionTokens.set(usage.completionTokens());
        lastPromptTokens.set(usage.promptTokens());
    }

    public long promptTokens() {
        return promptTokens.get();
    }

    public long completionTokens() {
        return completionTokens.get();
    }

    public long cachedTokens() {
        return cachedTokens.get();
    }

    public long totalTokens() {
        return promptTokens.get() + completionTokens.get();
    }

    public long llmCalls() {
        return llmCalls.get();
    }

    /** 最近一次调用的输入 token 数（= 当前上下文占用）。 */
    public long lastPromptTokens() {
        return lastPromptTokens.get();
    }

    public TokenUsage snapshot() {
        return new TokenUsage((int) promptTokens.get(), (int) completionTokens.get(), (int) cachedTokens.get());
    }

    /** 输出速度（tok/s）：仅按最近一次调用的完成量与耗时算，累计平均会把首轮冷启动摊平。 */
    public double tokensPerSecond() {
        long elapsed = lastElapsedMs.get();
        if (elapsed <= 0) {
            return 0d;
        }
        return lastCompletionTokens.get() * 1000d / elapsed;
    }

    public UsageReport report() {
        return new UsageReport(promptTokens.get(), completionTokens.get(), cachedTokens.get(),
                llmCalls.get(), lastElapsedMs.get(), tokensPerSecond(), lastPromptTokens.get());
    }
}
