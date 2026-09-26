package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmProtocol;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;

/**
 * LLM 协议适配器：一种协议一个实现（openai / anthropic / ollama）。
 */
public interface LlmAdapter {

    /** 本适配器负责的协议。 */
    LlmProtocol protocol();

    /** 发起流式生成（通过 emitter 推送增量）。 */
    Flow.Publisher<StreamChunk> generate(GenerateOptions options);
}
