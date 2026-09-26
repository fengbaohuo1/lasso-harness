package com.jnf.harness.model;

/**
 * 工具执行结果：成功内容或结构化失败（失败返回给模型看，不抛异常）。
 */
public record ToolExecutionResult(boolean success, String content, String errorCode) {

    public ToolExecutionResult {
        content = content == null ? "" : content;
    }

    public static ToolExecutionResult ok(String content) {
        return new ToolExecutionResult(true, content, null);
    }

    public static ToolExecutionResult fail(String message, String errorCode) {
        return new ToolExecutionResult(false, message, errorCode);
    }

    public boolean failed() {
        return !success;
    }
}
