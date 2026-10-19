package com.jnf.harness.plugin.web;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.host.WebService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Web 搜索（依赖宿主 {@link WebService#search}；未配置搜索服务时返回结构化失败）。
 */
public class WebSearchTool extends AbstractTool {

    private final WebService web;

    /** @param web 宿主服务；可空（宿主未提供时工具降级报错） */
    public WebSearchTool(WebService web) {
        this.web = web;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "搜索网页，返回结果摘要列表。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("query", stringSchema("搜索关键词"))
                .required("query")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String query = str(args, "query");
        if (query.isBlank()) {
            return fail("query 不能为空", "MISSING_QUERY");
        }
        if (web == null) {
            return fail("宿主未提供 WebService（插件降级）", "NO_HOST_SERVICE");
        }
        WebService.WebOutcome result = web.search(query);
        return result.success() ? ok(result.content())
                : fail(result.content(), result.errorCode());
    }
}
