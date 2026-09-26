package com.jnf.harness.domain.llm;

import com.jnf.harness.domain.llm.model.valobj.LlmFailure;

/**
 * LLM 调用异常：携带结构化 {@link LlmFailure}（错误码 + 是否可重试）。
 */
public class LlmException extends RuntimeException {

    private final transient LlmFailure failure;

    public LlmException(LlmFailure failure) {
        super(failure == null ? "llm failure" : failure.message());
        this.failure = failure == null
                ? new LlmFailure(null, "llm failure", false) : failure;
    }

    public LlmFailure failure() {
        return failure;
    }

    public boolean retryable() {
        return failure.retryable();
    }
}
