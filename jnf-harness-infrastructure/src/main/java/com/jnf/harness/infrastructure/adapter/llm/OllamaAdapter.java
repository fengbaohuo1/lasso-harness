package com.jnf.harness.infrastructure.adapter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.llm.LlmException;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmErrorCodes;
import com.jnf.harness.domain.llm.model.valobj.LlmFailure;
import com.jnf.harness.domain.llm.model.valobj.LlmProtocol;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Ollama 原生协议适配器（POST {baseUrl}/api/chat，NDJSON 流）。
 */
public class OllamaAdapter implements LlmAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore;

    public OllamaAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(300), null);
    }

    /** P11-4：带附件库的构造面——图片按 Ollama 原生 {@code message.images}（裸 base64）传送。 */
    public OllamaAdapter(com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(300), attachmentStore);
    }

    public OllamaAdapter(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, null);
    }

    public OllamaAdapter(HttpClient httpClient, Duration requestTimeout,
                         com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.attachmentStore = attachmentStore;
    }

    /**
     * 消息装配：复用 OpenAI 映射器（role/tool_calls 一致），再把 parts 数组还原成 Ollama 形状
     * （{@code content} 字符串 + {@code images} 裸 base64 数组）。
     */
    private List<Map<String, Object>> buildMessages(GenerateOptions options) {
        List<Map<String, Object>> messages = OpenAiMessageMapper.toOpenAiMessages(
                options.messages(), this::toDataUri);
        for (Map<String, Object> message : messages) {
            if (!(message.get("content") instanceof List<?> parts)) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            List<String> images = new ArrayList<>();
            for (Object part : parts) {
                if (!(part instanceof Map<?, ?> map)) {
                    continue;
                }
                if ("text".equals(map.get("type"))) {
                    text.append(map.get("text"));
                } else if ("image_url".equals(map.get("type")) && map.get("image_url") instanceof Map<?, ?> url) {
                    String value = String.valueOf(url.get("url"));
                    int comma = value.indexOf(',');
                    images.add(comma > 0 ? value.substring(comma + 1) : value);
                }
            }
            message.put("content", text.toString());
            if (!images.isEmpty()) {
                message.put("images", images);
            }
        }
        return messages;
    }

    private String toDataUri(com.jnf.harness.model.ImageAttachmentRef ref) {
        return attachmentStore == null ? null : attachmentStore.toDataUri(ref.mediaType(), ref.dataRef());
    }

    @Override
    public LlmProtocol protocol() {
        return LlmProtocol.OLLAMA;
    }

    @Override
    public Flow.Publisher<StreamChunk> generate(GenerateOptions options) {
        return new StreamEmitter<>(emitter -> doGenerate(options, emitter));
    }

    private void doGenerate(GenerateOptions options, StreamEmitter<StreamChunk> emitter) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(options.channel().baseUrl()) + "/api/chat"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(options)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                emitter.error(new LlmException(mapHttpError(response.statusCode())));
                return;
            }

            FinishReason finishReason = null;
            int promptTokens = 0;
            int completionTokens = 0;
            for (String line : (Iterable<String>) response.body()::iterator) {
                if (emitter.isDone() || line == null || line.isBlank()) {
                    continue;
                }
                JsonNode root = MAPPER.readTree(line);
                JsonNode message = root.path("message");
                if (message.hasNonNull("content") && !message.get("content").asText().isEmpty()) {
                    emitter.next(new StreamChunk.TextDelta(message.get("content").asText()));
                }
                JsonNode toolCalls = message.path("tool_calls");
                if (toolCalls.isArray()) {
                    int index = 0;
                    for (JsonNode toolCall : toolCalls) {
                        String name = toolCall.path("function").path("name").asText("");
                        String args = toolCall.path("function").path("arguments").isContainerNode()
                                ? toolCall.path("function").path("arguments").toString()
                                : toolCall.path("function").path("arguments").asText("{}");
                        emitter.next(new StreamChunk.ToolCallDelta("call_" + index++, name, args));
                        finishReason = FinishReason.TOOL_CALLS;
                    }
                }
                if (root.path("done").asBoolean(false)) {
                    promptTokens = root.path("prompt_eval_count").asInt(0);
                    completionTokens = root.path("eval_count").asInt(0);
                    if (finishReason == null) {
                        finishReason = FinishReason.STOP;
                    }
                }
            }
            emitter.next(new StreamChunk.DoneChunk(
                    finishReason == null ? FinishReason.STOP : finishReason,
                    new TokenUsage(promptTokens, completionTokens)));
            emitter.complete();
        } catch (LlmException e) {
            emitter.error(e);
        } catch (IOException e) {
            emitter.error(new LlmException(LlmFailure.timeout("Ollama IO 失败: " + e.getMessage())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.error(new LlmException(new LlmFailure(LlmErrorCodes.TIMEOUT, "调用被中断", false)));
        } catch (Exception e) {
            emitter.error(new LlmException(new LlmFailure(
                    LlmErrorCodes.MALFORMED_RESPONSE, "Ollama 响应解析失败: " + e.getMessage(), false)));
        }
    }

    private String buildBody(GenerateOptions options) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());
        body.put("messages", buildMessages(options));
        body.put("stream", true);
        if (!options.toolSchemas().isEmpty()) {
            body.put("tools", options.toolSchemas());
        }
        // P11-2：输出上限（Ollama 走 options.num_predict；此前完全不可控）
        if (options.maxTokens() != null) {
            body.put("options", Map.of("num_predict", options.maxTokens()));
        }
        // P9-8 思考强度：Ollama think 开关（off=false，其余=true）
        if (options.reasoningEffort() != null) {
            body.put("think", !"off".equals(options.reasoningEffort()));
        }
        return MAPPER.writeValueAsString(body);
    }

    private static LlmFailure mapHttpError(int status) {
        return switch (status) {
            case 401, 403 -> new LlmFailure(LlmErrorCodes.AUTH_FAILED, "鉴权失败 HTTP " + status, false);
            case 429 -> new LlmFailure(LlmErrorCodes.RATE_LIMITED, "限流 HTTP " + status, true);
            default -> new LlmFailure(LlmErrorCodes.UPSTREAM_ERROR, "上游错误 HTTP " + status, status >= 500);
        };
    }

    private static String trimSlash(String baseUrl) {
        String url = baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
