package com.jnf.harness.domain.llm.model.valobj;

import com.jnf.harness.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次生成请求的完整输入（值对象）。
 */
public record GenerateOptions(LlmChannel channel, String model,
                              List<Message> messages, List<Map<String, Object>> toolSchemas,
                              Integer maxTokens, Double temperature) {

    public GenerateOptions {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(model, "model");
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolSchemas = toolSchemas == null ? List.of() : List.copyOf(toolSchemas);
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

        public GenerateOptions build() {
            return new GenerateOptions(channel, model, messages, toolSchemas, maxTokens, temperature);
        }
    }
}
