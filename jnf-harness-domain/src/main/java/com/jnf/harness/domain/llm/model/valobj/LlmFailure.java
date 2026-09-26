package com.jnf.harness.domain.llm.model.valobj;

/**
 * LLM 调用失败（结构化，携带是否可重试）。
 */
public record LlmFailure(String code, String message, boolean retryable) {

    public LlmFailure {
        code = code == null ? LlmErrorCodes.UPSTREAM_ERROR : code;
        message = message == null ? "" : message;
    }

    public static LlmFailure timeout(String message) {
        return new LlmFailure(LlmErrorCodes.TIMEOUT, message, true);
    }

    public static LlmFailure upstream(String message, boolean retryable) {
        return new LlmFailure(LlmErrorCodes.UPSTREAM_ERROR, message, retryable);
    }
}
