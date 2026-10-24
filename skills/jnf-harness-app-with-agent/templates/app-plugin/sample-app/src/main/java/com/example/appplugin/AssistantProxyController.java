package com.example.appplugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 助手服务端代理：浏览器只调应用自己的 {@code POST /api/assistant/stream}，
 * 服务端带上 API Key 转发到 harness {@code /api/agent/stream}，把 SSE 原样透传。
 *
 * <p>为什么要代理：
 * <ul>
 *   <li>API Key 只存在应用服务端，绝不进浏览器；</li>
 *   <li>浏览器只访问应用域，没有 CORS / 跨域凭证问题；</li>
 *   <li>应用可以在转发前做鉴权、限流、审计、按用户注入 agentId。</li>
 * </ul>
 *
 * <p>配置：{@code harness.base-url}（默认 http://127.0.0.1:8090）、{@code harness.api-key}（可空）。
 * 请求体只取 {@code message} / {@code agentId}，其它参数（model/cwd/profile）由服务端决定，避免前端越权。
 */
@RestController
public class AssistantProxyController {

    private static final MediaType EVENT_STREAM = MediaType.parseMediaType("text/event-stream;charset=UTF-8");
    private static final String DEFAULT_AGENT = "app-e2e";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String streamUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public AssistantProxyController(@Value("${harness.base-url:http://127.0.0.1:8090}") String baseUrl,
                                    @Value("${harness.api-key:}") String apiKey) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.streamUrl = base + "/api/agent/stream";
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @PostMapping(value = "/api/assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody(required = false) Map<String, Object> body) {
        String message = body == null ? "" : str(body, "message").trim();
        if (message.isEmpty()) {
            // 握手后无法改 HTTP 状态码：按底座契约回一帧 error，前端统一处理
            return sse(output -> writeErrorFrame(output, "40000", "message 不能为空"));
        }
        String agentId = str(body, "agentId").trim();
        if (agentId.isEmpty()) {
            agentId = DEFAULT_AGENT;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agentId);
        payload.put("message", message);
        final String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return sse(output -> writeErrorFrame(output, "50000", "请求体序列化失败：" + e.getMessage()));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(streamUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));
        if (!apiKey.isEmpty()) {
            builder.header("X-API-Key", apiKey);
        }
        final HttpRequest request = builder.build();
        return sse(output -> forward(request, output));
    }

    /** 把底座 SSE 逐行原样透传；空行（帧边界）时 flush，保证前端逐帧可见。 */
    private void forward(HttpRequest request, OutputStream output) throws Exception {
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String text = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            writeErrorFrame(output, "HARNESS_HTTP_" + response.statusCode(),
                    "底座返回 HTTP " + response.statusCode() + "：" + text);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                if (line.isEmpty()) {
                    output.flush();
                }
            }
            output.flush();
        }
    }

    private ResponseEntity<StreamingResponseBody> sse(StreamWriter writer) {
        StreamingResponseBody body = output -> {
            try {
                writer.write(output);
            } catch (Exception e) {
                try {
                    writeErrorFrame(output, "PROXY_ERROR",
                            "转发 " + streamUrl + " 失败：" + e.getMessage()
                                    + "（确认底座已启动、harness.base-url / harness.api-key 正确）");
                } catch (Exception ignored) {
                    // 响应可能已提交，无法再写错误帧
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private static void writeErrorFrame(OutputStream output, String code, String message) throws java.io.IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        output.write(("event: error\ndata: " + MAPPER.writeValueAsString(payload) + "\n\n")
                .getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /** 允许抛受检异常的 SSE 写出器。 */
    @FunctionalInterface
    private interface StreamWriter {
        void write(OutputStream output) throws Exception;
    }
}
