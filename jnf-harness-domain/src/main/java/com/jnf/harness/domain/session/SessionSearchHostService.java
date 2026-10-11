package com.jnf.harness.domain.session;

import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.spi.host.SessionSearchService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SessionSearchService} 宿主实现（A3）：扫描会话事件存储（原 session_search 工具的检索逻辑）。
 * <p>工具已外移为 jnf-harness-plugin-essentials，检索策略留宿主，插件只呈现。
 */
public class SessionSearchHostService implements SessionSearchService {

    private final ISessionEventStore eventStore;

    public SessionSearchHostService(ISessionEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public List<SearchHit> search(String query, int maxHits) {
        String needle = query == null ? "" : query.toLowerCase();
        if (needle.isBlank() || maxHits <= 0) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (String sessionId : eventStore.listSessionIds()) {
            for (SessionEventEntry entry : eventStore.load(sessionId)) {
                String text = textOf(entry.event());
                if (!text.isEmpty() && text.toLowerCase().contains(needle)) {
                    hits.add(new SearchHit(sessionId, text));
                    if (hits.size() >= maxHits) {
                        return hits;
                    }
                }
            }
        }
        return hits;
    }

    private String textOf(SessionEvent event) {
        if (event instanceof SessionEvent.UserMessage m) {
            return m.text();
        }
        if (event instanceof SessionEvent.AssistantMessage m) {
            return m.text();
        }
        if (event instanceof SessionEvent.ToolResult r) {
            return r.content();
        }
        return "";
    }
}
