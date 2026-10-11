package com.jnf.harness.domain.agent.model;

import com.jnf.harness.domain.llm.model.valobj.LlmChannel;

/**
 * Agent 运行选项（值对象）。
 * <p>A1 画像：七个旋钮 + 工具画像码 + 提示词补丁——场景差异的主体是参数差异，
 * 由 {@code harness_agent_profile} 画像数据按 turn 覆盖，不碰引擎。
 */
public record AgentOptions(String model, LlmChannel channel,
                           int maxStepsPerTurn, int maxTokenContinuations,
                           long contextTokenBudget, int keepRecentMessages,
                           long compactionThresholdTokens, Integer maxTokens, Double temperature,
                           String toolProfile, String promptExtra) {

    /** 工具画像缺省码（接 R5 工具目录画像绑定）。 */
    public static final String DEFAULT_TOOL_PROFILE = "default";

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
        toolProfile = (toolProfile == null || toolProfile.isBlank()) ? DEFAULT_TOOL_PROFILE : toolProfile;
    }

    /** 兼容旧构造面（无画像语义，工具画像=default、无提示词补丁）。 */
    public AgentOptions(String model, LlmChannel channel,
                        int maxStepsPerTurn, int maxTokenContinuations,
                        long contextTokenBudget, int keepRecentMessages,
                        long compactionThresholdTokens, Integer maxTokens, Double temperature) {
        this(model, channel, maxStepsPerTurn, maxTokenContinuations, contextTokenBudget,
                keepRecentMessages, compactionThresholdTokens, maxTokens, temperature,
                DEFAULT_TOOL_PROFILE, null);
    }

    public static AgentOptions of(String model, LlmChannel channel) {
        return new AgentOptions(model, channel, 50, 4, 128_000, 6, 100_000, null, null,
                DEFAULT_TOOL_PROFILE, null);
    }
}
