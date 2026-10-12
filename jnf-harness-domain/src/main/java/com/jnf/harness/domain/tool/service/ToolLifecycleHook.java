package com.jnf.harness.domain.tool.service;

import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.PluginHookResult;

import java.util.Map;

/**
 * 工具生命周期 Hook 缝（插件 PRE/POST_TOOL_USE 的接入点，第 9 节接线）。
 * <p>before 可拦截（BLOCK/DENY）；after 只观察。
 */
public interface ToolLifecycleHook {

    default PluginHookResult beforeToolUse(String toolName, Map<String, Object> arguments) {
        return PluginHookResult.allow();
    }

    /** 会话感知形态（P9-2）：携带真实 ToolRunContext，默认回落无会话形态。 */
    default PluginHookResult beforeToolUse(ToolRunContext context, String toolName,
                                           Map<String, Object> arguments) {
        return beforeToolUse(toolName, arguments);
    }

    default void afterToolUse(String toolName, Map<String, Object> arguments, ToolExecutionResult result) {
    }

    /** 会话感知形态（P9-2）：携带真实 ToolRunContext，默认回落无会话形态。 */
    default void afterToolUse(ToolRunContext context, String toolName,
                              Map<String, Object> arguments, ToolExecutionResult result) {
        afterToolUse(toolName, arguments, result);
    }
}
