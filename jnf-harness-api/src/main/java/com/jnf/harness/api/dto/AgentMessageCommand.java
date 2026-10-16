package com.jnf.harness.api.dto;

import com.jnf.harness.model.ImageAttachmentRef;

import java.util.List;

/**
 * Agent 对话命令。
 *
 * @param profile     智能体预设码（A1/P9-7，可空=default：步数/预算/工具画像/提示词补丁按预设裁）
 * @param thinking    思考强度提示（P9-8，off/low/medium/high，可空=预设值）
 * @param attachments 图片附件引用（P11-4：已落附件库的引用，只有纯图片消息时 {@code message} 可为空）
 */
public record AgentMessageCommand(String agentId, String message, String model,
                                  Integer maxTokens, Double temperature, String cwd,
                                  String profile, String thinking,
                                  List<ImageAttachmentRef> attachments) {

    public AgentMessageCommand {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 必填");
        }
        boolean hasText = message != null && !message.isBlank();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        if (!hasText && !hasAttachments) {
            throw new IllegalArgumentException("message 与 attachments 不能同时为空");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** 兼容旧构造面（无附件）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd,
                               String profile, String thinking) {
        this(agentId, message, model, maxTokens, temperature, cwd, profile, thinking, List.of());
    }

    /** 兼容旧构造面（无画像/思考强度提示）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd) {
        this(agentId, message, model, maxTokens, temperature, cwd, null, null, List.of());
    }

    /** 兼容旧构造面（无思考强度提示）。 */
    public AgentMessageCommand(String agentId, String message, String model,
                               Integer maxTokens, Double temperature, String cwd,
                               String profile) {
        this(agentId, message, model, maxTokens, temperature, cwd, profile, null, List.of());
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
