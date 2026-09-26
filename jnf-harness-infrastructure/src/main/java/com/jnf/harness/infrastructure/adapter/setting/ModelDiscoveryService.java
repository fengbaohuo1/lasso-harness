package com.jnf.harness.infrastructure.adapter.setting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型发现（OpenAI 兼容 /models；失败结构化上抛）。
 */
public class ModelDiscoveryService implements IModelDiscoveryPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public List<String> discover(String baseUrl, String apiKey, String protocol) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 必填");
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String target = "ollama".equalsIgnoreCase(protocol) ? url + "/api/tags" : url + "/models";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("上游返回 HTTP " + response.statusCode());
            }
            JsonNode root = MAPPER.readTree(response.body());
            List<String> models = new ArrayList<>();
            if (root.path("data").isArray()) {
                root.path("data").forEach(node -> models.add(node.path("id").asText("")));
            } else if (root.path("models").isArray()) {
                root.path("models").forEach(node -> models.add(node.path("name").asText("")));
            }
            models.removeIf(String::isBlank);
            return models;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("模型发现失败: " + e.getMessage());
        }
    }
}
