package com.jnf.harness.domain.agent.profile;

/**
 * 智能体预设（P9-7，由 A1 画像拓展）：一份「模型偏好/思考强度/步数/预算/工具白名单/提示词补丁」
 * 配置，即 dsh 的「模式选择」——用户可配置、添加、在新会话选用，整个 session 固定不变。
 *
 * @param description     预设说明（可空，选择器里展示）
 * @param modelCode       模型偏好（可空；命中 modelCode 的渠道整渠道切换，未命中作模型名覆盖）
 * @param reasoningEffort 思考强度：off/low/medium/high（可空=不传）
 * @param toolProfile     工具画像码（接 R5 harness_tool_profile_binding；工具 schema 按它现算）
 * @param promptExtra     追加进系统提示词的场景指令（可空）
 */
public record AgentProfile(String profileCode, String displayName, String description,
                           String modelCode, String reasoningEffort,
                           int maxStepsPerTurn, int maxTokenContinuations,
                           long contextTokenBudget, int keepRecentMessages,
                           long compactionThresholdTokens, Integer maxTokens, Double temperature,
                           String toolProfile, String promptExtra) {

    public static final String DEFAULT_CODE = "default";
    public static final int MAX_PROMPT_EXTRA_LENGTH = 4_000;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    /** 思考强度合法值（与 GenerateOptions 同口径，null=不传）。 */
    public static final java.util.Set<String> REASONING_EFFORTS =
            java.util.Set.of("off", "low", "medium", "high");

    /** 缺省预设（与引擎历史默认值一致）。 */
    public static AgentProfile defaults() {
        return new AgentProfile(DEFAULT_CODE, "默认预设", "内置缺省：通用对话与工具调用", null, null,
                50, 4, 128_000, 6, 100_000, null, null, DEFAULT_CODE, null);
    }

    public AgentProfile {
        if (profileCode == null || profileCode.isBlank()) {
            throw new IllegalArgumentException("profileCode 必填");
        }
        profileCode = profileCode.trim();
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description 最长 " + MAX_DESCRIPTION_LENGTH + " 字符");
        }
        if (modelCode != null) {
            modelCode = modelCode.trim();
            modelCode = modelCode.isEmpty() ? null : modelCode;
        }
        if (reasoningEffort != null) {
            reasoningEffort = reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
            if (reasoningEffort.isEmpty()) {
                reasoningEffort = null;
            } else if (!REASONING_EFFORTS.contains(reasoningEffort)) {
                throw new IllegalArgumentException("reasoningEffort 只能是 off/low/medium/high");
            }
        }
        toolProfile = (toolProfile == null || toolProfile.isBlank()) ? DEFAULT_CODE : toolProfile.trim();
        if (promptExtra != null && promptExtra.length() > MAX_PROMPT_EXTRA_LENGTH) {
            throw new IllegalArgumentException("promptExtra 最长 " + MAX_PROMPT_EXTRA_LENGTH + " 字符");
        }
    }

    /** 兼容旧构造面（无预设语义：无描述/模型偏好/思考强度）。 */
    public AgentProfile(String profileCode, String displayName,
                       int maxStepsPerTurn, int maxTokenContinuations,
                       long contextTokenBudget, int keepRecentMessages,
                       long compactionThresholdTokens, Integer maxTokens, Double temperature,
                       String toolProfile, String promptExtra) {
        this(profileCode, displayName, null, null, null,
                maxStepsPerTurn, maxTokenContinuations, contextTokenBudget, keepRecentMessages,
                compactionThresholdTokens, maxTokens, temperature, toolProfile, promptExtra);
    }
}
