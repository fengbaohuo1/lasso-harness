package com.jnf.harness.domain.session;

import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * session_search 工具：按关键词搜索历史会话消息（跨会话，读事件存储）。
 */
public class SessionSearchTool extends AbstractTool {

    private static final int MAX_HITS = 50;

    private final ISessionEventStore eventStore;

    public SessionSearchTool(ISessionEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public String name() {
        return "session_search";
    }

    @Override
    public String description() {
        return "按关键词搜索历史会话消息，返回 会话id:消息文本（最多 50 条）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema().prop("query", stringSchema("关键词")).required("query").build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String query = str(args, "query").toLowerCase();
        if (query.isBlank()) {
            return fail("query 不能为空", "MISSING_QUERY");
        }
        List<String> hits = new ArrayList<>();
        for (String sessionId : eventStore.listSessionIds()) {
            for (SessionEventEntry entry : eventStore.load(sessionId)) {
                String text = textOf(entry.event());
                if (!text.isEmpty() && text.toLowerCase().contains(query)) {
                    hits.add(sessionId + ": " + text);
                    if (hits.size() >= MAX_HITS) {
                        break;
                    }
                }
            }
            if (hits.size() >= MAX_HITS) {
                break;
            }
        }
        return ok(hits.isEmpty() ? "（无命中）" : String.join("\n", hits));
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
