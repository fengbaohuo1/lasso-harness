package com.jnf.harness.domain.session.event;

import java.util.List;
import java.util.Map;

/**
 * 会话事件（sealed 封闭家族）：会话 = 事件日志，一切状态可由事件重放重建。
 */
public sealed interface SessionEvent
        permits SessionEvent.TurnStart, SessionEvent.TurnEnd,
        SessionEvent.StepStart, SessionEvent.StepEnd,
        SessionEvent.UserMessage, SessionEvent.AssistantChunk, SessionEvent.ReasoningChunk,
        SessionEvent.AssistantMessage,
        SessionEvent.ToolCall, SessionEvent.ToolResult, SessionEvent.Compacted, SessionEvent.Usage {

    record TurnStart(long turn) implements SessionEvent {
    }

    record TurnEnd(long turn, TurnEndReason reason, String detail) implements SessionEvent {
    }

    record StepStart(long turn, long step) implements SessionEvent {
    }

    record StepEnd(long turn, long step) implements SessionEvent {
    }

    /**
     * 用户消息（P11-4：可带图片附件引用；纯图片消息 text 为空串）。
     */
    record UserMessage(long turn, long step, String text,
                       List<com.jnf.harness.model.ImageAttachmentRef> attachments) implements SessionEvent {

        public UserMessage {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }

        /** 兼容旧构造面（纯文本消息）。 */
        public UserMessage(long turn, long step, String text) {
            this(turn, step, text, List.of());
        }

        public boolean hasAttachments() {
            return !attachments.isEmpty();
        }
    }

    /** 文本流增量（仅 UI 呈现用；重放以 AssistantMessage 为准）。 */
    record AssistantChunk(long turn, long step, String text) implements SessionEvent {
    }

    /** 思考内容流增量（P9-8：仅 UI 呈现/回放用，不进 deriveMessages 上下文）。 */
    record ReasoningChunk(long turn, long step, String text) implements SessionEvent {
    }

    record AssistantMessage(long turn, long step, String text) implements SessionEvent {
    }

    record ToolCall(long turn, long step, String callId, String name,
                    Map<String, Object> arguments) implements SessionEvent {
    }

    record ToolResult(long turn, long step, String callId, String name,
                      boolean error, String content) implements SessionEvent {
    }

    /** 压缩事件：summary 概括 sourceSeqs 范围的历史（见压缩引擎）。 */
    record Compacted(String summary, List<Long> sourceSeqs) implements SessionEvent {
    }

    /**
     * 每次 LLM 调用的 token 用量（R11 计量落事件，重启后仍可聚合）。
     * <p>P11-5：补采 {@code cachedTokens}（提示词缓存命中）与 {@code elapsedMs}（本次调用墙钟耗时），
     * 让前端能显示缓存命中率与输出速度。
     */
    record Usage(long turn, long step, long promptTokens, long completionTokens,
                 long cachedTokens, long elapsedMs) implements SessionEvent {

        /** 兼容旧构造面（无缓存/耗时统计）。 */
        public Usage(long turn, long step, long promptTokens, long completionTokens) {
            this(turn, step, promptTokens, completionTokens, 0L, 0L);
        }
    }
}
