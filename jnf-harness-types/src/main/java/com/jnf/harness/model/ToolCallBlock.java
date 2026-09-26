package com.jnf.harness.model;

import java.util.Map;
import java.util.Objects;

/**
 * 工具调用块：模型发起的一次工具调用（callId 用于与 {@link ToolResultBlock} 配对）。
 */
public record ToolCallBlock(String callId, String name, Map<String, Object> arguments)
        implements ContentBlock {

    public ToolCallBlock {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(name, "name");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public static ToolCallBlock of(String callId, String name, Map<String, Object> arguments) {
        return new ToolCallBlock(callId, name, arguments);
    }
}
