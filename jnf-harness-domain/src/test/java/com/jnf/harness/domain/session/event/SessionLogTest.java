package com.jnf.harness.domain.session.event;

import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话事件日志验收：seq 分配、消息投影、压缩替换（第 3 节 DoD）。
 */
class SessionLogTest {

    @Test
    @DisplayName("追加分配自增 seq（会话内有序）")
    void assigns_sequential_seq() {
        SessionLog log = new SessionLog();
        assertEquals(1, log.append(new SessionEvent.TurnStart(1)).seq());
        assertEquals(2, log.append(new SessionEvent.UserMessage(1, 1, "hi")).seq());
        assertEquals(3, log.append(new SessionEvent.TurnEnd(1, TurnEndReason.COMPLETED, "")).seq());
        assertEquals(3, log.size());
    }

    @Test
    @DisplayName("deriveMessages：user/assistant/tool 消息按序投影")
    void derives_messages_in_order() {
        SessionLog log = new SessionLog();
        log.append(new SessionEvent.TurnStart(1));
        log.append(new SessionEvent.UserMessage(1, 1, "查天气"));
        log.append(new SessionEvent.AssistantChunk(1, 1, "调"));
        log.append(new SessionEvent.AssistantChunk(1, 1, "工具"));
        log.append(new SessionEvent.ToolCall(1, 1, "call_1", "weather", Map.of("city", "杭州")));
        log.append(new SessionEvent.ToolResult(1, 1, "call_1", "weather", false, "晴"));
        log.append(new SessionEvent.AssistantMessage(1, 1, "今天晴。"));

        List<Message> messages = log.deriveMessages();
        assertEquals(4, messages.size());
        assertEquals(MessageSource.USER, messages.get(0).source());
        assertEquals("查天气", messages.get(0).text());
        ToolCallBlock call = assertInstanceOf(ToolCallBlock.class, messages.get(1).blocks().get(0));
        assertEquals("call_1", call.callId());
        assertEquals("杭州", call.arguments().get("city"));
        ToolResultBlock result = assertInstanceOf(ToolResultBlock.class, messages.get(2).blocks().get(0));
        assertEquals("晴", result.content());
        assertEquals(MessageSource.ASSISTANT, messages.get(3).source());
    }

    @Test
    @DisplayName("Compacted 事件用摘要替换被概括的历史")
    void compaction_replaces_history() {
        SessionLog log = new SessionLog();
        log.append(new SessionEvent.UserMessage(1, 1, "问题A"));
        log.append(new SessionEvent.AssistantMessage(1, 1, "回答A"));
        log.append(new SessionEvent.UserMessage(2, 1, "问题B"));
        log.append(new SessionEvent.AssistantMessage(2, 1, "回答B"));
        log.append(new SessionEvent.Compacted("用户问了A", List.of(1L, 2L)));

        List<Message> messages = log.deriveMessages();
        assertEquals(3, messages.size());
        assertTrue(messages.get(0).text().contains("用户问了A"));
        assertEquals("问题B", messages.get(1).text());
        assertEquals("回答B", messages.get(2).text());
    }
}
