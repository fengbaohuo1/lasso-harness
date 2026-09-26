package com.jnf.harness.domain.runtime.meter;

import com.jnf.harness.domain.llm.model.valobj.TokenUsage;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话级 token 计量（修正项 R11：计量作为一等能力）。
 */
public class SessionTokenMeter {

    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong llmCalls = new AtomicLong();

    public void record(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
        llmCalls.incrementAndGet();
    }

    public long promptTokens() {
        return promptTokens.get();
    }

    public long completionTokens() {
        return completionTokens.get();
    }

    public long totalTokens() {
        return promptTokens.get() + completionTokens.get();
    }

    public long llmCalls() {
        return llmCalls.get();
    }

    public TokenUsage snapshot() {
        return new TokenUsage((int) promptTokens.get(), (int) completionTokens.get());
    }
}
