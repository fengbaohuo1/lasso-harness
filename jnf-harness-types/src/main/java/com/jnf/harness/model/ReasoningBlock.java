package com.jnf.harness.model;

import java.util.Objects;

/**
 * 模型思考（reasoning）内容块。
 */
public record ReasoningBlock(String text) implements ContentBlock {

    public ReasoningBlock {
        Objects.requireNonNull(text, "text");
    }

    public static ReasoningBlock of(String text) {
        return new ReasoningBlock(text);
    }
}
