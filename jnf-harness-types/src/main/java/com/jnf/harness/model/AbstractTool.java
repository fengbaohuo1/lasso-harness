package com.jnf.harness.model;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 工具基类：实现 {@link ToolDefinition} 的模板，子类只需给 name/description/parameters/run。
 * <p>失败一律收敛为 {@link ToolExecutionResult#fail}，不向上抛异常——让模型看到结构化错误并换思路。
 */
public abstract class AbstractTool implements ToolDefinition {

    @Override
    public abstract String name();

    @Override
    public abstract String description();

    @Override
    public abstract Map<String, Object> parameters();

    /** 工具实现体。 */
    protected abstract CompletableFuture<ToolExecutionResult> run(
            Map<String, Object> arguments, ToolRunContext context);

    @Override
    public final CompletableFuture<ToolExecutionResult> execute(
            Map<String, Object> arguments, ToolRunContext context) {
        Objects.requireNonNull(context, "context");
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        try {
            return run(args, context);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail(name() + " failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }

    // ---- 子类便捷方法 ----

    protected static CompletableFuture<ToolExecutionResult> ok(String content) {
        return CompletableFuture.completedFuture(ToolExecutionResult.ok(content));
    }

    protected static CompletableFuture<ToolExecutionResult> fail(String message, String errorCode) {
        return CompletableFuture.completedFuture(ToolExecutionResult.fail(message, errorCode));
    }

    protected static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    protected static ToolSchema.Builder objectSchema() {
        return ToolSchema.objectSchema();
    }

    protected static Map<String, Object> stringSchema(String description) {
        return ToolSchema.stringSchema(description);
    }
}
