package com.jnf.harness.domain.llm.model.valobj;

/**
 * LLM 调用错误码（跨适配器归一）。
 */
public final class LlmErrorCodes {

    public static final String TIMEOUT = "LLM_TIMEOUT";
    public static final String RATE_LIMITED = "LLM_RATE_LIMITED";
    public static final String AUTH_FAILED = "LLM_AUTH_FAILED";
    public static final String UPSTREAM_ERROR = "LLM_UPSTREAM_ERROR";
    public static final String MALFORMED_RESPONSE = "LLM_MALFORMED_RESPONSE";
    public static final String UNSUPPORTED_PROTOCOL = "LLM_UNSUPPORTED_PROTOCOL";

    private LlmErrorCodes() {
    }
}
