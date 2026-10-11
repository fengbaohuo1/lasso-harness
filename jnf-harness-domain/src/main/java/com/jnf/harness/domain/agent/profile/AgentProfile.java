package com.jnf.harness.domain.agent.profile;

/**
 * 智能体画像（A1）：一份「步数/预算/工具白名单/提示词补丁」配置，按场景裁运行开销。
 *
 * @param toolProfile 工具画像码（接 R5 harness_tool_profile_binding；工具 schema 按它现算）
 * @param promptExtra 追加进系统提示词的场景指令（可空）
 */
public record AgentProfile(String profileCode, String displayName,
                           int maxStepsPerTurn, int maxTokenContinuations,
                           long contextTokenBudget, int keepRecentMessages,
                           long compactionThresholdTokens, Integer maxTokens, Double temperature,
                           String toolProfile, String promptExtra) {

    public static final String DEFAULT_CODE = "default";
    public static final int MAX_PROMPT_EXTRA_LENGTH = 4_000;

    /** 缺省画像（与引擎历史默认值一致）。 */
    public static AgentProfile defaults() {
        return new AgentProfile(DEFAULT_CODE, "默认画像", 50, 4, 128_000, 6, 100_000,
                null, null, DEFAULT_CODE, null);
    }

    public AgentProfile {
        if (profileCode == null || profileCode.isBlank()) {
            throw new IllegalArgumentException("profileCode 必填");
        }
        profileCode = profileCode.trim();
        toolProfile = (toolProfile == null || toolProfile.isBlank()) ? DEFAULT_CODE : toolProfile.trim();
        if (promptExtra != null && promptExtra.length() > MAX_PROMPT_EXTRA_LENGTH) {
            throw new IllegalArgumentException("promptExtra 最长 " + MAX_PROMPT_EXTRA_LENGTH + " 字符");
        }
    }
}
