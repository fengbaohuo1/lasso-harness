package com.jnf.harness.infrastructure.adapter.web;

import com.jnf.harness.domain.tool.web.IWebPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP Web 端口实现：fetch 真实抓取（HTML 去标签）；search 依赖外部服务，未配置时结构化失败。
 */
public class HttpWebPort implements IWebPort {

    private final HttpClient httpClient;
    private final Duration timeout;

    public HttpWebPort() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofSeconds(30));
    }

    public HttpWebPort(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout;
    }

    @Override
    public Result fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", "jnf-harness/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Result.fail("抓取失败 HTTP " + response.statusCode(), "FETCH_HTTP_ERROR");
            }
            return Result.ok(stripHtml(response.body()));
        } catch (Exception e) {
            return Result.fail("抓取异常: " + e.getMessage(), "FETCH_ERROR");
        }
    }

    @Override
    public Result search(String query) {
        // 搜索服务未接入（可配置外部搜索 API 后在此实现）
        return Result.fail("搜索服务未配置（WEB_SEARCH_NOT_CONFIGURED）", "WEB_SEARCH_NOT_CONFIGURED");
    }

    static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s{3,}", "\n\n")
                .trim();
    }
}
