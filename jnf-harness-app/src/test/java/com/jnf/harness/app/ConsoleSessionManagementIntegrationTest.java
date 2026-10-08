package com.jnf.harness.app;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.trigger.http.HarnessConsoleQueryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3/F6 控制台会话管理：列表增强（title/eventCount/时间）→ 重命名 → 软删过滤 → usage 聚合。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.events.dir=target/test-events-console",
                "spring.datasource.url=jdbc:h2:mem:dbconsole;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class ConsoleSessionManagementIntegrationTest {

    @Autowired
    HarnessConsoleQueryController controller;

    @Autowired
    ISessionEventStore eventStore;

    @Test
    @DisplayName("列表增强 → 重命名 → 空标题 40000 → 软删后列表过滤 → usage 聚合")
    void session_lifecycle() {
        String sessionId = "s-console-" + UUID.randomUUID();
        long seq = 0;
        eventStore.append(sessionId, new SessionEventEntry(++seq, new SessionEvent.TurnStart(1)));
        eventStore.append(sessionId, new SessionEventEntry(++seq,
                new SessionEvent.UserMessage(1, 1, "你好")));
        eventStore.append(sessionId, new SessionEventEntry(++seq,
                new SessionEvent.Usage(1, 1, 100, 20)));
        eventStore.append(sessionId, new SessionEventEntry(++seq,
                new SessionEvent.Usage(1, 2, 30, 5)));
        eventStore.append(sessionId, new SessionEventEntry(++seq, new SessionEvent.TurnStart(2)));
        eventStore.append(sessionId, new SessionEventEntry(++seq,
                new SessionEvent.Usage(2, 1, 10, 2)));

        // 列表增强
        Map<String, Object> item = findItem(controller.sessions().data(), sessionId);
        assertEquals(6, item.get("eventCount"));
        assertTrue((Long) item.get("createdAt") > 0);
        assertTrue((Long) item.get("lastEventAt") > 0);

        // 重命名
        Response<Map<String, Object>> renamed = controller.rename(sessionId, Map.of("title", "改造计划"));
        assertEquals("00000", renamed.code());
        assertEquals("改造计划", findItem(controller.sessions().data(), sessionId).get("title"));

        // 空标题业务错
        assertEquals("40000", controller.rename(sessionId, Map.of("title", "   ")).code());

        // usage 聚合（prompt=140, completion=27, total=167, turns=2）
        Map<String, Object> usage = controller.usage(sessionId).data();
        assertEquals(140L, usage.get("promptTokens"));
        assertEquals(27L, usage.get("completionTokens"));
        assertEquals(167L, usage.get("totalTokens"));
        assertEquals(2L, usage.get("turns"));

        // 软删后列表过滤（事件文件保留，可回放）
        assertEquals("00000", controller.delete(sessionId).code());
        assertFalse(controller.sessions().data().stream().anyMatch(i -> sessionId.equals(i.get("sessionId"))));
        assertFalse(controller.messages(sessionId).data().isEmpty());
    }

    private Map<String, Object> findItem(List<Map<String, Object>> items, String sessionId) {
        return items.stream()
                .filter(i -> sessionId.equals(i.get("sessionId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("列表缺少会话: " + sessionId));
    }
}
