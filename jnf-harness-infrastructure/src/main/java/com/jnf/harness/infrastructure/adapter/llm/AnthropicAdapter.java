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
import com.jnf.harness.model.ContentBlock;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Anthropic Messages 协议适配器（POST {baseUrl}/v1/messages，SSE 事件流）。
 * <p>事件映射：content_block_delta(text_delta/thinking_delta/input_json_delta) → 增量；
 * message_delta.stop_reason → DoneChunk。
 */
public class AnthropicAdapter implements LlmAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore;

    public AnthropicAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(120), null);
    }

    /** P11-4：带附件库的构造面——图片块还原成 Anthropic image source（base64）。 */
    public AnthropicAdapter(com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(120), attachmentStore);
    }

    public AnthropicAdapter(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, null);
    }

    public AnthropicAdapter(HttpClient httpClient, Duration requestTimeout,
                            com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.attachmentStore = attachmentStore;
    }

    @Override
    public LlmProtocol protocol() {
        return LlmProtocol.ANTHROPIC;
    }

    @Override
    public Flow.Publisher<StreamChunk> generate(GenerateOptions options) {
        return new StreamEmitter<>(emitter -> doGenerate(options, emitter));
    }

    private void doGenerate(GenerateOptions options, StreamEmitter<StreamChunk> emitter) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(options.channel().baseUrl()) + "/v1/messages"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("x-api-key", options.channel().apiKey())
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(options)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                emitter.error(new LlmException(mapHttpError(response.statusCode())));
                return;
            }

            // index → 块类型/工具标识
            Map<Integer, String> blockKinds = new HashMap<>();
            Map<Integer, String[]> toolIds = new HashMap<>();
            FinishReason finishReason = null;
            int promptTokens = 0;
            int completionTokens = 0;
            int cachedTokens = 0;

            for (String line : (Iterable<String>) response.body()::iterator) {
                if (emitter.isDone()) {
                    break;
                }
                if (line == null || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                JsonNode root = MAPPER.readTree(payload);
                String type = root.path("type").asText("");
                switch (type) {
                    case "message_start" -> {
                        JsonNode usage = root.path("message").path("usage");
                        promptTokens = usage.path("input_tokens").asInt(0);
                        // P11-5：Anthropic 的缓存命中写作 cache_read_input_tokens
                        cachedTokens = usage.path("cache_read_input_tokens").asInt(0);
                    }
                    case "content_block_start" -> {
                        int index = root.path("index").asInt(0);
                        JsonNode block = root.path("content_block");
                        String blockType = block.path("type").asText("text");
                        blockKinds.put(index, blockType);
                        if ("tool_use".equals(blockType)) {
                            toolIds.put(index, new String[]{
                                    block.path("id").asText("call_" + index), block.path("name").asText("")});
                            emitter.next(new StreamChunk.ToolCallDelta(
                                    toolIds.get(index)[0], toolIds.get(index)[1], ""));
                        }
                    }
                    case "content_block_delta" -> {
                        int index = root.path("index").asInt(0);
                        JsonNode delta = root.path("delta");
                        switch (delta.path("type").asText("")) {
                            case "text_delta" -> emitter.next(new StreamChunk.TextDelta(
                                    delta.path("text").asText("")));
                            case "thinking_delta" -> emitter.next(new StreamChunk.ReasoningDelta(
                                    delta.path("thinking").asText("")));
                            case "input_json_delta" -> {
                                String[] ids = toolIds.getOrDefault(index, new String[]{"call_" + index, ""});
                                emitter.next(new StreamChunk.ToolCallDelta(
                                        ids[0], ids[1], delta.path("partial_json").asText("")));
                            }
                            default -> {
                                // 其他增量类型忽略
                            }
                        }
                    }
                    case "message_delta" -> {
                        finishReason = FinishReason.from(root.path("delta").path("stop_reason").asText(""));
                        completionTokens = root.path("usage").path("output_tokens").asInt(0);
                    }
                    case "error" -> emitter.error(new LlmException(new LlmFailure(
                            LlmErrorCodes.UPSTREAM_ERROR,
                            root.path("error").path("message").asText("anthropic error"), true)));
                    default -> {
                        // message_stop / ping 等忽略
                    }
                }
            }
            emitter.next(new StreamChunk.DoneChunk(
                    finishReason == null ? FinishReason.STOP : finishReason,
                    new TokenUsage(promptTokens, completionTokens, cachedTokens)));
            emitter.complete();
        } catch (LlmException e) {
            emitter.error(e);
        } catch (IOException e) {
            emitter.error(new LlmException(LlmFailure.timeout("Anthropic IO 失败: " + e.getMessage())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.error(new LlmException(new LlmFailure(LlmErrorCodes.TIMEOUT, "调用被中断", false)));
        } catch (Exception e) {
            emitter.error(new LlmException(new LlmFailure(
                    LlmErrorCodes.MALFORMED_RESPONSE, "Anthropic 响应解析失败: " + e.getMessage(), false)));
        }
    }

    private String buildBody(GenerateOptions options) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());
        body.put("max_tokens", options.maxTokens() == null ? 4096 : options.maxTokens());
        body.put("stream", true);

        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : options.messages()) {
            if (message.source() == MessageSource.SYSTEM) {
                system.append(message.text());
                continue;
            }
            messages.addAll(toAnthropicMessage(message));
        }
        if (!system.isEmpty()) {
            body.put("system", system.toString());
        }
        body.put("messages", messages);
        if (!options.toolSchemas().isEmpty()) {
            body.put("tools", options.toolSchemas());
        }
        // P9-8 思考强度：Anthropic thinking 块（off=显式关闭，low/medium/high=按预算开启）
        if (options.reasoningEffort() != null) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            if ("off".equals(options.reasoningEffort())) {
                thinking.put("type", "disabled");
            } else {
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", switch (options.reasoningEffort()) {
                    case "low" -> 2048;
                    case "high" -> 24576;
                    default -> 8192;
                });
            }
            body.put("thinking", thinking);
        }
        return MAPPER.writeValueAsString(body);
    }

    private List<Map<String, Object>> toAnthropicMessage(Message message) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> userContent = new ArrayList<>();
        List<Map<String, Object>> assistantContent = new ArrayList<>();
        List<Map<String, Object>> imageContent = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        for (ContentBlock block : message.blocks()) {
            if (block instanceof com.jnf.harness.model.TextBlock t) {
                text.append(t.text());
            } else if (block instanceof com.jnf.harness.model.ImageBlock image) {
                // P11-4：Anthropic 的 content 本来就是数组，图片直接作为 image 块插入 user 消息
                Map<String, Object> source = imageSource(image.ref());
                if (source != null) {
                    imageContent.add(Map.of("type", "image", "source", source));
                }
            } else if (block instanceof ToolCallBlock call) {
                Map<String, Object> toolUse = new LinkedHashMap<>();
                toolUse.put("type", "tool_use");
                toolUse.put("id", call.callId());
                toolUse.put("name", call.name());
                toolUse.put("input", call.arguments());
                assistantContent.add(toolUse);
            } else if (block instanceof ToolResultBlock result) {
                Map<String, Object> toolResult = new LinkedHashMap<>();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", result.callId());
                toolResult.put("content", result.content());
                toolResult.put("is_error", result.error());
                userContent.add(toolResult);
            }
        }

        if (message.source() == MessageSource.TOOL) {
            // 工具结果进 user 消息（Anthropic 语义）
            if (!userContent.isEmpty()) {
                out.add(Map.of("role", "user", "content", userContent));
            }
            return out;
        }

        String role = message.source() == MessageSource.ASSISTANT ? "assistant" : "user";
        List<Map<String, Object>> content = new ArrayList<>();
        if (text.length() > 0) {
            content.add(Map.of("type", "text", "text", text.toString()));
        }
        content.addAll(imageContent);
        content.addAll(message.source() == MessageSource.ASSISTANT ? assistantContent : List.of());
        if (!content.isEmpty()) {
            out.add(Map.of("role", role, "content", content));
        }
        return out;
    }

    /** 附件引用 → Anthropic image source（base64 + media_type）；读不到返回 null。 */
    private Map<String, Object> imageSource(com.jnf.harness.model.ImageAttachmentRef ref) {
        if (attachmentStore == null) {
            return null;
        }
        java.util.Optional<byte[]> bytes = attachmentStore.read(ref.dataRef());
        if (bytes.isEmpty()) {
            return null;
        }
        return Map.of("type", "base64", "media_type", ref.mediaType(),
                "data", java.util.Base64.getEncoder().encodeToString(bytes.get()));
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
