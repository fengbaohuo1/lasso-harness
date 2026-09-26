package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.domain.llm.LlmException;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.llm.model.valobj.LlmErrorCodes;
import com.jnf.harness.domain.llm.model.valobj.LlmFailure;
import com.jnf.harness.domain.llm.model.valobj.LlmProtocol;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 网关验收：协议路由 + 受控重试（可重试且未产出增量才重试）。
 */
class LlmRuntimeGatewayTest {

    /** 可编程的假适配器：前 N 次失败，之后产出文本块。 */
    static final class StubAdapter implements LlmAdapter {
        final AtomicInteger attempts = new AtomicInteger();
        final int failuresFirst;
        final LlmProtocol protocol;
        final boolean retryable;

        StubAdapter(LlmProtocol protocol, int failuresFirst, boolean retryable) {
            this.protocol = protocol;
            this.failuresFirst = failuresFirst;
            this.retryable = retryable;
        }

        @Override
        public LlmProtocol protocol() {
            return protocol;
        }

        @Override
        public Flow.Publisher<StreamChunk> generate(GenerateOptions options) {
            return new StreamEmitter<>(emitter -> {
                int attempt = attempts.incrementAndGet();
                if (attempt <= failuresFirst) {
                    emitter.error(new LlmException(
                            new LlmFailure(LlmErrorCodes.UPSTREAM_ERROR, "boom " + attempt, retryable)));
                    return;
                }
                emitter.next(new StreamChunk.TextDelta("ok-" + attempt));
                emitter.next(new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero()));
                emitter.complete();
            });
        }
    }

    private GenerateOptions options(LlmProtocol protocol) {
        return GenerateOptions.builder()
                .channel(new LlmChannel("http://127.0.0.1:1", "k", protocol))
                .model("m")
                .messages(List.of(Message.ofText(MessageSource.USER, "hi")))
                .build();
    }

    private List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher, Throwable[] error) {
        List<StreamChunk> chunks = new ArrayList<>();
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
        return chunks;
    }

    @Test
    @DisplayName("可重试失败在产出前重试，成功后透传增量")
    void retries_before_emission() {
        StubAdapter stub = new StubAdapter(LlmProtocol.OPENAI, 2, true);
        LlmRuntimeGateway gateway = new LlmRuntimeGateway(List.of(stub), 3);

        Throwable[] error = new Throwable[1];
        List<StreamChunk> chunks = collect(gateway.stream(options(LlmProtocol.OPENAI)), error);

        assertEquals(3, stub.attempts.get());
        assertTrue(error[0] == null);
        assertEquals("ok-3", ((StreamChunk.TextDelta) chunks.get(0)).text());
    }

    @Test
    @DisplayName("不可重试失败立即上抛，不浪费重试")
    void fails_fast_on_non_retryable() {
        StubAdapter stub = new StubAdapter(LlmProtocol.OPENAI, 5, false);
        LlmRuntimeGateway gateway = new LlmRuntimeGateway(List.of(stub), 3);

        Throwable[] error = new Throwable[1];
        collect(gateway.stream(options(LlmProtocol.OPENAI)), error);

        assertEquals(1, stub.attempts.get());
        assertInstanceOf(LlmException.class, error[0]);
    }

    @Test
    @DisplayName("未注册协议归一为 UNSUPPORTED_PROTOCOL")
    void unsupported_protocol() {
        LlmRuntimeGateway gateway = new LlmRuntimeGateway(List.of(), 3);
        Throwable[] error = new Throwable[1];
        collect(gateway.stream(options(LlmProtocol.ANTHROPIC)), error);

        LlmException e = assertInstanceOf(LlmException.class, error[0]);
        assertEquals(LlmErrorCodes.UNSUPPORTED_PROTOCOL, e.failure().code());
    }
}
