package com.jnf.harness.infrastructure.adapter.eventstore;

import com.jnf.harness.domain.session.event.PersistingSessionLog;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.domain.session.event.TurnEndReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * JSONL 事件存储验收：编解码往返 + 写穿持久化 + 重启重建（第 3 节 DoD）。
 */
class JsonlSessionEventStoreTest {

    @Test
    @DisplayName("十类事件 JSONL 往返相等")
    void codec_round_trip(@TempDir Path tempDir) throws Exception {
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir);
        List<SessionEvent> events = List.of(
                new SessionEvent.TurnStart(1),
                new SessionEvent.TurnEnd(1, TurnEndReason.COMPLETED, "done"),
                new SessionEvent.StepStart(1, 1),
                new SessionEvent.StepEnd(1, 1),
                new SessionEvent.UserMessage(1, 1, "你好"),
                new SessionEvent.AssistantChunk(1, 1, "增"),
                new SessionEvent.AssistantMessage(1, 1, "增量拼装"),
                new SessionEvent.ToolCall(1, 1, "call_1", "t", Map.of("k", "v")),
                new SessionEvent.ToolResult(1, 1, "call_1", "t", true, "失败"),
                new SessionEvent.Compacted("摘要", List.of(1L, 2L)));

        int seq = 1;
        for (SessionEvent event : events) {
            store.append("s-1", new SessionEventEntry(seq++, event));
        }

        List<SessionEventEntry> loaded = store.load("s-1");
        assertEquals(events.size(), loaded.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1, loaded.get(i).seq());
            assertEquals(events.get(i), loaded.get(i).event());
        }
    }

    @Test
    @DisplayName("PersistingSessionLog 写穿：重启后由存储重建等价消息")
    void write_through_and_rebuild(@TempDir Path tempDir) {
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir);
        PersistingSessionLog log = new PersistingSessionLog("s-2", new SessionLog(), store);
        log.append(new SessionEvent.UserMessage(1, 1, "问题"));
        log.append(new SessionEvent.ToolCall(1, 1, "c1", "tool", Map.of("a", 1)));
        log.append(new SessionEvent.ToolResult(1, 1, "c1", "tool", false, "结果"));
        log.append(new SessionEvent.AssistantMessage(1, 1, "答案"));

        // 重建：新 SessionLog 从存储加载
        SessionLog rebuilt = new SessionLog();
        rebuilt.appendBatch(store.load("s-2").stream().map(SessionEventEntry::event).toList());

        assertEquals(log.log().deriveMessages(), rebuilt.deriveMessages());
        assertEquals(4, rebuilt.size());
        assertInstanceOf(SessionEvent.ToolResult.class,
                store.load("s-2").get(2).event());
    }
}
