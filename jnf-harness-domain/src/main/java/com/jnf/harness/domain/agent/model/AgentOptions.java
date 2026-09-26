package com.jnf.harness.domain.agent.model;

import com.jnf.harness.domain.llm.model.valobj.LlmChannel;

/**
 * Agent 运行选项（值对象）。
 */
public record AgentOptions(String model, LlmChannel channel,
                           int maxStepsPerTurn, int maxTokenContinuations,
                           long contextTokenBudget, int keepRecentMessages,
                           long compactionThresholdTokens, Integer maxTokens, Double temperature) {

    public AgentOptions {
        if (maxStepsPerTurn <= 0) {
            maxStepsPerTurn = 50;
        }
        if (maxTokenContinuations <= 0) {
            maxTokenContinuations = 4;
        }
        if (contextTokenBudget <= 0) {
            contextTokenBudget = 128_000;
        }
        if (keepRecentMessages <= 0) {
            keepRecentMessages = 6;
        }
        if (compactionThresholdTokens <= 0) {
            compactionThresholdTokens = 100_000;
        }
    }

    public static AgentOptions of(String model, LlmChannel channel) {
        return new AgentOptions(model, channel, 50, 4, 128_000, 6, 100_000, null, null);
    }
}
