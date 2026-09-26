package com.jnf.harness.api.dto;

/**
 * Agent 一轮对话的结果。
 */
public record AgentMessageResult(String agentId, long turn, String status,
                                 String text, int toolCalls) {
}
