package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.llm.model.valobj.LlmProtocol;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.service.BlockAssembler;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Anthropic / Ollama 适配器验收：事件流 → StreamChunk → BlockAssembler 装配。
 */
class AnthropicAndOllamaAdapterTest {

    private HttpServer server;
    private volatile String anthropicBody;
    private volatile String ollamaBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            byte[] bytes = (anthropicBody == null ? "" : anthropicBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            byte[] bytes = (ollamaBody == null ? "" : ollamaBody).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GenerateOptions options(LlmProtocol protocol) {
        return GenerateOptions.builder()
                .channel(new LlmChannel("http://127.0.0.1:" + server.getAddress().getPort(), "k", protocol))
                .model("m")
                .messages(List.of(Message.ofText(MessageSource.USER, "hi")))
                .build();
    }

    private BlockAssembler.GenerationResult run(LlmProtocol protocol, Flow.Publisher<StreamChunk> publisher) {
        BlockAssembler assembler = new BlockAssembler();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk chunk) {
                assembler.accept(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return assembler.result();
    }

    @Test
    @DisplayName("Anthropic：text/thinking/input_json 增量装配 + stop_reason 归一")
    void anthropic_stream_assembles() {
        anthropicBody = """
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":7}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"思考"}}

                event: content_block_start
                data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_9","name":"fs_read"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"path\\":\\"a\\"}"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":5}}

                event: message_stop
                data: {"type":"message_stop"}
                """;
        BlockAssembler.GenerationResult result =
                run(LlmProtocol.ANTHROPIC, new AnthropicAdapter().generate(options(LlmProtocol.ANTHROPIC)));

        assertEquals("你好", result.text());
        assertEquals("思考", result.reasoning());
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_9", result.toolCalls().get(0).callId());
        assertEquals("a", result.toolCalls().get(0).arguments().get("path"));
        assertEquals(FinishReason.TOOL_CALLS, result.finishReason());
        assertEquals(7, result.usage().promptTokens());
        assertEquals(12, result.usage().totalTokens());
    }

    @Test
    @DisplayName("Ollama：NDJSON 流装配 + eval 计量")
    void ollama_stream_assembles() {
        ollamaBody = """
                {"message":{"role":"assistant","content":"局部"},"done":false}
                {"message":{"role":"assistant","content":"文本"},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"prompt_eval_count":4,"eval_count":6}
                """;
        BlockAssembler.GenerationResult result =
                run(LlmProtocol.OLLAMA, new OllamaAdapter().generate(options(LlmProtocol.OLLAMA)));

        assertEquals("局部文本", result.text());
        assertEquals(FinishReason.STOP, result.finishReason());
        assertEquals(4, result.usage().promptTokens());
        assertEquals(10, result.usage().totalTokens());
        assertNotNull(result.reasoning());
    }
}
