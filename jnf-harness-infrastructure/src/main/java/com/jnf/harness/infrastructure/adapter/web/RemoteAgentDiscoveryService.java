package com.jnf.harness.infrastructure.adapter.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 远端 Agent Card 发现：探测 {@code /.well-known/} 下的卡片（DSH v1 / A2A 双协议）。
 */
public class RemoteAgentDiscoveryService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * 探测远端卡片 JSON：优先 A2A 标准卡，回落 DSH 卡；失败返回错误 JSON。
     */
    public String discover(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        for (String path : new String[]{"/.well-known/agent-card.json",
                "/.well-known/agent.json", "/.well-known/dsh-agent-card"}) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && !response.body().isBlank()) {
                    return response.body();
                }
            } catch (Exception ignored) {
                // 探测下一个路径
            }
        }
        return "{\"error\":\"AGENT_CARD_NOT_FOUND\"}";
    }
}
