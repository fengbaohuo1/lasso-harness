package com.jnf.harness.api.dto;

/**
 * Agent 对话命令。
 */
public record AgentMessageCommand(String agentId, String message, String model,
                                  Integer maxTokens, Double temperature, String cwd) {

    public AgentMessageCommand {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 必填");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 必填");
        }
    }
}
