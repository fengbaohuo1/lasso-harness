package com.jnf.harness.domain.llm.adapter.port;

import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;

/**
 * LLM 运行时端口：流式生成。
 * <p>实现层职责：协议路由、HTTP 通信、重试、错误归一为 {@link LlmFailure} 语义
 * （错误以 onError(LlmException) 传递，见 infrastructure 适配器）。
 */
public interface ILlmRuntimePort {

    /** 发起流式生成；订阅后逐块推送 StreamChunk，最终 DoneChunk 后完成。 */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);
}
