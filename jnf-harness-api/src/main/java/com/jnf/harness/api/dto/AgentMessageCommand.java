package com.jnf.harness.api.dto;

/**
 * Agent 对话命令。
 *
 * @param profile  智能体预设码（A1/P9-7，可空=default：步数/预算/工具画像/提示词补丁按预设裁）
 * @param thinking 思考强度提示（P9-8，off/low/medium/high，可空=预设值）
 */
public record AgentMessageCommand(String agentId, String message, String model,
                                  Integer maxTokens, Double temperature, String cwd,
                                  String profile, String thinking) {

    /** 兼容旧构造面（无画像/思考强度提示）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd) {
        this(agentId, message, model, maxTokens, temperature, cwd, null, null);
    }

    /** 兼容旧构造面（无思考强度提示）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd,
                               String profile) {
        this(agentId, message, model, maxTokens, temperature, cwd, profile, null);
    }

    public AgentMessageCommand {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 必填");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 必填");
        }
    }
}
