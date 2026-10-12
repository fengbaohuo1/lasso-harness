package com.jnf.harness.domain.llm.model.valobj;

import com.jnf.harness.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次生成请求的完整输入（值对象）。
 *
 * @param reasoningEffort 思考强度：off/low/medium/high，null 表示不传（由供应商默认）
 */
public record GenerateOptions(LlmChannel channel, String model,
                              List<Message> messages, List<Map<String, Object>> toolSchemas,
                              Integer maxTokens, Double temperature, String reasoningEffort) {

    /** 思考强度合法值（null=不传）。 */
    public static final java.util.Set<String> REASONING_EFFORTS =
            java.util.Set.of("off", "low", "medium", "high");

    public GenerateOptions {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(model, "model");
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolSchemas = toolSchemas == null ? List.of() : List.copyOf(toolSchemas);
        if (reasoningEffort != null) {
            reasoningEffort = reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
            if (reasoningEffort.isEmpty()) {
                reasoningEffort = null;
            } else if (!REASONING_EFFORTS.contains(reasoningEffort)) {
                throw new IllegalArgumentException("reasoningEffort 只能是 off/low/medium/high");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private LlmChannel channel;
        private String model;
        private List<Message> messages = List.of();
        private List<Map<String, Object>> toolSchemas = List.of();
        private Integer maxTokens;
        private Double temperature;
        private String reasoningEffort;

        public Builder channel(LlmChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder toolSchemas(List<Map<String, Object>> toolSchemas) {
            this.toolSchemas = toolSchemas;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public GenerateOptions build() {
            return new GenerateOptions(channel, model, messages, toolSchemas, maxTokens, temperature,
                    reasoningEffort);
        }
    }
}
