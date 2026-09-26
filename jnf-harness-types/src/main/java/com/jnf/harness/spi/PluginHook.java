package com.jnf.harness.spi;

import java.util.Map;

/**
 * 插件 Hook：在工具执行前后被调用（hookPoint：PRE_TOOL_USE / POST_TOOL_USE）。
 * <p>PRE Hook 可拦截工具调用；POST Hook 只观察结果。
 */
@FunctionalInterface
public interface PluginHook {

    PluginHookResult evaluate(String toolName, Map<String, Object> arguments);
}
