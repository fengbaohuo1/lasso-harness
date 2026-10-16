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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * OpenAI 兼容协议适配器（POST {baseUrl}/chat/completions，SSE 流式）。
 * <p>覆盖 OpenAI / DeepSeek / 通义 / GLM 等 openai 兼容网关。
 */
public class OpenAiCompatibleAdapter implements LlmAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore;

    public OpenAiCompatibleAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(120), null);
    }

    /** P11-4：带附件库的构造面——图片块经它还原成 data URI 后进 parts 数组。 */
    public OpenAiCompatibleAdapter(com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(120), attachmentStore);
    }

    public OpenAiCompatibleAdapter(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, null);
    }

    public OpenAiCompatibleAdapter(HttpClient httpClient, Duration requestTimeout,
                                   com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.attachmentStore = attachmentStore;
    }

    /** 附件引用 → data URI；无附件库时返回 null（图片块被丢弃）。 */
    private String toDataUri(com.jnf.harness.model.ImageAttachmentRef ref) {
        return attachmentStore == null ? null : attachmentStore.toDataUri(ref.mediaType(), ref.dataRef());
    }

    @Override
    public LlmProtocol protocol() {
        return LlmProtocol.OPENAI;
    }

    @Override
    public Flow.Publisher<StreamChunk> generate(GenerateOptions options) {
        return new StreamEmitter<>(emitter -> doGenerate(options, emitter));
    }

    private void doGenerate(GenerateOptions options, StreamEmitter<StreamChunk> emitter) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(options.channel().baseUrl()) + "/chat/completions"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + options.channel().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(options)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                emitter.error(new LlmException(mapHttpError(response.statusCode())));
                return;
            }

            FinishReason finishReason = null;
            TokenUsage usage = TokenUsage.zero();
            // 工具调用按流内 index 归并：id 只出现在首片，后续参数增量不带 id
            java.util.Map<Integer, String> indexToCallId = new java.util.HashMap<>();
            for (String line : (Iterable<String>) response.body()::iterator) {
                if (emitter.isDone()) {
                    break;
                }
                if (line == null || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode root = MAPPER.readTree(payload);
                JsonNode choices = root.path("choices");
                for (JsonNode choice : choices) {
                    JsonNode delta = choice.path("delta");
                    if (delta.hasNonNull("content")) {
                        emitter.next(new StreamChunk.TextDelta(delta.get("content").asText()));
                    }
                    if (delta.hasNonNull("reasoning_content")) {
                        emitter.next(new StreamChunk.ReasoningDelta(delta.get("reasoning_content").asText()));
                    }
                    if (delta.hasNonNull("reasoning")) {
                        emitter.next(new StreamChunk.ReasoningDelta(delta.get("reasoning").asText()));
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode toolCall : toolCalls) {
                            int index = toolCall.path("index").asInt(0);
                            String callId = indexToCallId.computeIfAbsent(index,
                                    i -> toolCall.hasNonNull("id") ? toolCall.get("id").asText() : "call_" + i);
                            String name = toolCall.path("function").path("name").asText("");
                            String args = toolCall.path("function").path("arguments").asText("");
                            emitter.next(new StreamChunk.ToolCallDelta(callId, name, args));
                        }
                    }
                    if (choice.hasNonNull("finish_reason")) {
                        finishReason = FinishReason.from(choice.get("finish_reason").asText());
                    }
                }
                JsonNode usageNode = root.path("usage");
                if (!usageNode.isMissingNode() && !usageNode.isNull()) {
                    // P11-5：缓存命中量口径——OpenAI 系在 prompt_tokens_details.cached_tokens，
                    // DeepSeek 另给 prompt_cache_hit_tokens；两者取其一（有前者用前者）
                    int cached = usageNode.path("prompt_tokens_details").path("cached_tokens").asInt(0);
                    if (cached == 0) {
                        cached = usageNode.path("prompt_cache_hit_tokens").asInt(0);
                    }
                    usage = new TokenUsage(usageNode.path("prompt_tokens").asInt(0),
                            usageNode.path("completion_tokens").asInt(0), cached);
                }
            }
            emitter.next(new StreamChunk.DoneChunk(
                    finishReason == null ? FinishReason.STOP : finishReason, usage));
            emitter.complete();
        } catch (LlmException e) {
            emitter.error(e);
        } catch (IOException e) {
            emitter.error(new LlmException(LlmFailure.timeout("LLM IO 失败: " + e.getMessage())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.error(new LlmException(new LlmFailure(LlmErrorCodes.TIMEOUT, "LLM 调用被中断", false)));
        } catch (Exception e) {
            emitter.error(new LlmException(new LlmFailure(
                    LlmErrorCodes.MALFORMED_RESPONSE, "LLM 响应解析失败: " + e.getMessage(), false)));
        }
    }

    private String buildBody(GenerateOptions options) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());
        body.put("messages", OpenAiMessageMapper.toOpenAiMessages(options.messages(), this::toDataUri));
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        if (!options.toolSchemas().isEmpty()) {
            body.put("tools", options.toolSchemas().stream()
                    .map(OpenAiCompatibleAdapter::wrapTool)
                    .toList());
        }
        if (options.maxTokens() != null) {
            body.put("max_tokens", options.maxTokens());
        }
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        // P9-8 思考强度：OpenAI 兼容口径 reasoning_effort（off 不传，由供应商默认）
        if (options.reasoningEffort() != null && !"off".equals(options.reasoningEffort())) {
            body.put("reasoning_effort", options.reasoningEffort());
        }
        return MAPPER.writeValueAsString(body);
    }

    private static Map<String, Object> wrapTool(Map<String, Object> schema) {
        // 容忍两种形态：裸 function 定义 或 已包 {"type":"function","function":{...}}
        if (schema.containsKey("function")) {
            return schema;
        }
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", schema.get("name"));
        fn.put("description", schema.getOrDefault("description", ""));
        fn.put("parameters", schema.getOrDefault("parameters", Map.of("type", "object")));
        return Map.of("type", "function", "function", fn);
    }

    private static LlmFailure mapHttpError(int status) {
        return switch (status) {
            case 401, 403 -> new LlmFailure(LlmErrorCodes.AUTH_FAILED, "鉴权失败 HTTP " + status, false);
            case 429 -> new LlmFailure(LlmErrorCodes.RATE_LIMITED, "限流 HTTP " + status, true);
            default -> new LlmFailure(LlmErrorCodes.UPSTREAM_ERROR,
                    "上游错误 HTTP " + status, status >= 500);
        };
    }

    private static String trimSlash(String baseUrl) {
        String url = baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
