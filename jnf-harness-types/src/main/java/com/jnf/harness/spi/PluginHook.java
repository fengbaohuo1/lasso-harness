package com.jnf.harness.spi;

import com.jnf.harness.model.ToolRunContext;

import java.util.Map;

/**
 * 插件 Hook：在工具执行前后被调用（hookPoint：PRE_TOOL_USE / POST_TOOL_USE）。
 * <p>PRE Hook 可拦截工具调用；POST Hook 只观察结果。
 */
@FunctionalInterface
public interface PluginHook {

    PluginHookResult evaluate(String toolName, Map<String, Object> arguments);

    /**
     * 会话感知评估（P9-2）：宿主优先调用此形态，携带真实 agentId/sessionId/cwd。
     * <p>默认回落无会话形态，老插件无感升级。
     */
    default PluginHookResult evaluate(ToolRunContext context, String toolName,
                                      Map<String, Object> arguments) {
        return evaluate(toolName, arguments);
    }
}
