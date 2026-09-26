package com.jnf.harness.model;

/**
 * 结构化失败描述（错误码 + 说明），供工具失败与 Hook 拦截统一表达。
 */
public record ToolFailure(String code, String message) {

    public ToolFailure {
        code = code == null ? "UNKNOWN" : code;
        message = message == null ? "" : message;
    }

    public static ToolFailure of(String code, String message) {
        return new ToolFailure(code, message);
    }
}
