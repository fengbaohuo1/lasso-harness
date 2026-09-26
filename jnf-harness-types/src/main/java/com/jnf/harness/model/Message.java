package com.jnf.harness.model;

import java.util.List;
import java.util.Objects;

/**
 * 一条对话消息：来源 + 若干内容块。
 */
public record Message(MessageSource source, List<ContentBlock> blocks) {

    public Message {
        Objects.requireNonNull(source, "source");
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static Message ofText(MessageSource source, String text) {
        return new Message(source, List.of(TextBlock.of(text)));
    }

    /** 把所有文本块拼接成纯文本（无文本块时返回空串）。 */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }
}
