package com.jnf.harness.plugin.essentials;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.host.SessionSearchService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * session_search 工具（A3 外移）：跨会话历史检索——扫描策略在宿主
 * {@link SessionSearchService}，本工具只做呈现。
 */
public class SessionSearchTool extends AbstractTool {

    private static final int MAX_HITS = 50;

    private final SessionSearchService searchService;

    /** @param searchService 宿主服务；可空（宿主未提供时降级报错） */
    public SessionSearchTool(SessionSearchService searchService) {
        this.searchService = searchService;
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
        String query = str(args, "query");
        if (query.isBlank()) {
            return fail("query 不能为空", "MISSING_QUERY");
        }
        if (searchService == null) {
            return fail("宿主未提供 SessionSearchService（插件降级）", "NO_HOST_SERVICE");
        }
        List<String> hits = new ArrayList<>();
        for (SessionSearchService.SearchHit hit : searchService.search(query, MAX_HITS)) {
            hits.add(hit.sessionId() + ": " + hit.text());
        }
        return ok(hits.isEmpty() ? "（无命中）" : String.join("\n", hits));
    }
}
