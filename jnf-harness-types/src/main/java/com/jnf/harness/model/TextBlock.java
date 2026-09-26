package com.jnf.harness.model;

import java.util.Objects;

/**
 * 文本内容块。
 */
public record TextBlock(String text) implements ContentBlock {

    public TextBlock {
        Objects.requireNonNull(text, "text");
    }

    public static TextBlock of(String text) {
        return new TextBlock(text);
    }
}
