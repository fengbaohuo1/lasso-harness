package com.jnf.harness.domain.llm.model.valobj;

import java.util.Objects;

/**
 * 模型渠道：一次 LLM 调用所需的接入信息（值对象，凭据引用由上层解析注入）。
 */
public record LlmChannel(String baseUrl, String apiKey, LlmProtocol protocol) {

    public LlmChannel {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(protocol, "protocol");
        apiKey = apiKey == null ? "" : apiKey;
    }

    public static LlmChannel openai(String baseUrl, String apiKey) {
        return new LlmChannel(baseUrl, apiKey, LlmProtocol.OPENAI);
    }
}
