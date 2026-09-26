package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.domain.llm.LlmException;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmErrorCodes;
import com.jnf.harness.domain.llm.model.valobj.LlmFailure;
import com.jnf.harness.domain.llm.model.valobj.LlmProtocol;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * LLM 运行时网关：按渠道协议路由到适配器 + 受控重试（重试收敛在端口实现层）。
 * <p>重试语义：仅在"可重试失败且尚未产出任何增量"时重试（已开始产出则不打断）。
 */
public class LlmRuntimeGateway implements ILlmRuntimePort {

    private final Map<LlmProtocol, LlmAdapter> adapters = new EnumMap<>(LlmProtocol.class);
    private final int maxAttempts;

    public LlmRuntimeGateway(java.util.List<LlmAdapter> adapters, int maxAttempts) {
        for (LlmAdapter adapter : adapters) {
            this.adapters.put(adapter.protocol(), adapter);
        }
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
        return new StreamEmitter<>(emitter -> {
            LlmAdapter adapter = adapters.get(options.channel().protocol());
            if (adapter == null) {
                emitter.error(new LlmException(new LlmFailure(LlmErrorCodes.UNSUPPORTED_PROTOCOL,
                        "未注册的协议适配器: " + options.channel().protocol(), false)));
                return;
            }
            new RetryForwarder(options, adapter, emitter).start();
        });
    }

    /** 转发 + 重试的订阅者（适配器流失败时按需重建订阅）。 */
    private final class RetryForwarder implements Flow.Subscriber<StreamChunk> {
        private final GenerateOptions options;
        private final LlmAdapter adapter;
        private final StreamEmitter<StreamChunk> emitter;
        private int attempt = 1;
        private boolean emitted;

        RetryForwarder(GenerateOptions options, LlmAdapter adapter, StreamEmitter<StreamChunk> emitter) {
            this.options = options;
            this.adapter = adapter;
            this.emitter = emitter;
        }

        void start() {
            adapter.generate(options).subscribe(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamChunk chunk) {
            emitted = true;
            emitter.next(chunk);
        }

        @Override
        public void onError(Throwable throwable) {
            LlmException failure = asLlmException(throwable);
            if (failure.retryable() && !emitted && attempt < maxAttempts && !emitter.isDone()) {
                attempt++;
                adapter.generate(options).subscribe(this);
                return;
            }
            emitter.error(failure);
        }

        @Override
        public void onComplete() {
            emitter.complete();
        }

        private LlmException asLlmException(Throwable throwable) {
            if (throwable instanceof LlmException e) {
                return e;
            }
            return new LlmException(LlmFailure.upstream(String.valueOf(throwable.getMessage()), true));
        }
    }
}
