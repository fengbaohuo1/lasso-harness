package com.jnf.harness.model;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具定义契约：Agent 可调用的一个能力。
 * <p>实现者通常继承 {@link AbstractTool}。
 */
public interface ToolDefinition {

    /** 工具名（LLM 可见；插件工具会被限定为 plugin__&lt;pluginId&gt;__&lt;toolName&gt;）。 */
    String name();

    /** 工具用途描述（LLM 可见，写清楚"何时用"）。 */
    String description();

    /** 参数 JSON Schema（object 类型，见 {@link ToolSchema}）。 */
    Map<String, Object> parameters();

    /** 执行工具。实现者一般覆写 {@link AbstractTool#run}。 */
    CompletableFuture<ToolExecutionResult> execute(Map<String, Object> arguments, ToolRunContext context);
}
