package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.domain.llm.LlmException;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.llm.model.valobj.LlmErrorCodes;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容适配器验收：对 fake SSE 服务器流式出 token、工具调用增量、HTTP 错误归一。
 */
class OpenAiCompatibleAdapterTest {

    private HttpServer server;
    private volatile String sseBody;
    private volatile int httpStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = (sseBody == null ? "" : sseBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GenerateOptions options() {
        LlmChannel channel = LlmChannel.openai(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
        return GenerateOptions.builder()
                .channel(channel)
                .model("test-model")
                .messages(List.of(Message.ofText(MessageSource.USER, "你好")))
                .build();
    }

    private List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher) {
        List<StreamChunk> chunks = new ArrayList<>();
        Throwable[] error = new Throwable[1];
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                error[0] = throwable;
            }

            @Override
            public void onComplete() {
            }
        });
        if (error[0] != null) {
            if (error[0] instanceof RuntimeException e) {
                throw e;
            }
            throw new IllegalStateException(error[0]);
        }
        return chunks;
    }

    @Test
    @DisplayName("流式文本增量 + 结束原因 + 用量")
    void streams_text_deltas_and_done() {
        sseBody = """
                data: {"choices":[{"delta":{"content":"你"}}]}

                data: {"choices":[{"delta":{"content":"好"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}}

                data: [DONE]
                """;
        List<StreamChunk> chunks = collect(new OpenAiCompatibleAdapter().generate(options()));

        assertEquals(2, chunks.stream().filter(c -> c instanceof StreamChunk.TextDelta).count());
        StreamChunk.DoneChunk done = (StreamChunk.DoneChunk) chunks.get(chunks.size() - 1);
        assertEquals(FinishReason.STOP, done.reason());
        assertEquals(3, done.usage().promptTokens());
        assertEquals(5, done.usage().totalTokens());
    }

    @Test
    @DisplayName("工具调用增量按 callId/name/参数分片透传")
    void streams_tool_call_deltas() {
        sseBody = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"fs_read","arguments":"{\\"pa"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\\":\\"a\\"}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<StreamChunk> chunks = collect(new OpenAiCompatibleAdapter().generate(options()));

        List<StreamChunk.ToolCallDelta> deltas = chunks.stream()
                .filter(c -> c instanceof StreamChunk.ToolCallDelta)
                .map(c -> (StreamChunk.ToolCallDelta) c)
                .toList();
        assertEquals(2, deltas.size());
        assertEquals("call_1", deltas.get(0).callId());
        assertEquals("fs_read", deltas.get(0).name());
        assertEquals("{\"pa", deltas.get(0).argumentsDelta());
        // 参数增量不带 id：必须按 index 归并到同一 callId（否则拆成两个残缺 call）
        assertEquals("call_1", deltas.get(1).callId());
        assertEquals("", deltas.get(1).name());
        assertEquals("th\":\"a\"}", deltas.get(1).argumentsDelta());
        assertEquals(FinishReason.TOOL_CALLS,
                ((StreamChunk.DoneChunk) chunks.get(chunks.size() - 1)).reason());
    }

    @Test
    @DisplayName("并行工具调用按 index 归并 + 装配出完整 ToolCallBlock（真实厂商回放不 400）")
    void parallel_tool_calls_merge_by_index() {
        sseBody = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","function":{"name":"fs_read","arguments":"{\\"pa"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"todo_write","arguments":"{\\"it"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\\":\\"a\\"}"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"ems\\":[]}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<StreamChunk> chunks = collect(new OpenAiCompatibleAdapter().generate(options()));

        com.jnf.harness.domain.llm.service.BlockAssembler assembler =
                new com.jnf.harness.domain.llm.service.BlockAssembler();
        chunks.forEach(assembler::accept);
        List<com.jnf.harness.model.ToolCallBlock> calls = assembler.result().toolCalls();

        assertEquals(2, calls.size());
        assertEquals("call_a", calls.get(0).callId());
        assertEquals("fs_read", calls.get(0).name());
        assertEquals("a", calls.get(0).arguments().get("path"));
        assertEquals("call_b", calls.get(1).callId());
        assertEquals("todo_write", calls.get(1).name());
    }

    @Test
    @DisplayName("无名残块不装配（防回放进下一轮请求被上游 400 拒收）")
    void drops_nameless_tool_call_blocks() {
        sseBody = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<StreamChunk> chunks = collect(new OpenAiCompatibleAdapter().generate(options()));

        com.jnf.harness.domain.llm.service.BlockAssembler assembler =
                new com.jnf.harness.domain.llm.service.BlockAssembler();
        chunks.forEach(assembler::accept);
        assertEquals(0, assembler.result().toolCalls().size());
    }

    @Test
    @DisplayName("HTTP 错误归一为 LlmException（429 可重试、401 不可重试）")
    void maps_http_errors() {
        httpStatus = 429;
        sseBody = "";
        LlmException e = assertThrows(LlmException.class,
                () -> collect(new OpenAiCompatibleAdapter().generate(options())));
        assertEquals(LlmErrorCodes.RATE_LIMITED, e.failure().code());
        assertTrue(e.retryable());

        httpStatus = 401;
        LlmException auth = assertThrows(LlmException.class,
                () -> collect(new OpenAiCompatibleAdapter().generate(options())));
        assertEquals(LlmErrorCodes.AUTH_FAILED, auth.failure().code());
        assertTrue(!auth.retryable());
        assertInstanceOf(LlmException.class, auth);
    }
}
