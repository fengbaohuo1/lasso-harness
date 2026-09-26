package com.jnf.harness.domain.plugin;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 插件工具限定名包装：注册名固定为 {@code plugin__<pluginId>__<toolName>}，
 * 描述/参数/执行全部委托原工具（模型看到限定名，调用回到插件实现）。
 */
record QualifiedTool(String pluginId, ToolDefinition delegate) implements ToolDefinition {

    @Override
    public String name() {
        return "plugin__" + pluginId + "__" + delegate.name();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Map<String, Object> parameters() {
        return delegate.parameters();
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(Map<String, Object> arguments, ToolRunContext context) {
        return delegate.execute(arguments, context);
    }
}
