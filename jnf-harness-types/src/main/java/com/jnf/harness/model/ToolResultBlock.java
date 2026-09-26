package com.jnf.harness.model;

import java.util.Objects;

/**
 * 工具结果块：一次工具调用的返回（与 {@link ToolCallBlock} 按 callId 配对）。
 */
public record ToolResultBlock(String callId, String name, boolean error, String content)
        implements ContentBlock {

    public ToolResultBlock {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(name, "name");
        content = content == null ? "" : content;
    }

    public static ToolResultBlock of(String callId, String name, String content) {
        return new ToolResultBlock(callId, name, false, content);
    }

    public static ToolResultBlock failure(String callId, String name, String content) {
        return new ToolResultBlock(callId, name, true, content);
    }
}
