package com.jnf.harness.domain.tool.web;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 抓取网页内容（纯文本）。
 */
public class WebFetchTool extends AbstractTool {

    private final IWebPort webPort;

    public WebFetchTool(IWebPort webPort) {
        this.webPort = webPort;
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "抓取指定 URL 的网页内容，返回纯文本。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("url", stringSchema("http/https URL"))
                .required("url")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String url = str(args, "url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return fail("仅支持 http/https URL", "INVALID_URL");
        }
        IWebPort.Result result = webPort.fetch(url);
        return result.success() ? ok(truncate(result.content()))
                : fail(result.content(), result.errorCode());
    }

    private String truncate(String content) {
        return content != null && content.length() > 32_000
                ? content.substring(0, 32_000) + "\n...（内容截断）" : content;
    }
}
