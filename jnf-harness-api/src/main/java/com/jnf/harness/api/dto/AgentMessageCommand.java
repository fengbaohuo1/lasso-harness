package com.jnf.harness.api.dto;

/**
 * Agent 对话命令。
 *
 * @param profile 智能体画像码（A1，可空=default：步数/预算/工具画像/提示词补丁按画像裁）
 */
public record AgentMessageCommand(String agentId, String message, String model,
                                  Integer maxTokens, Double temperature, String cwd,
                                  String profile) {

    /** 兼容旧构造面（无画像提示）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd) {
        this(agentId, message, model, maxTokens, temperature, cwd, null);
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
