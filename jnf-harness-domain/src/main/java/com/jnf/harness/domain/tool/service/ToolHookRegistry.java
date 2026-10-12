package com.jnf.harness.domain.tool.service;

import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.spi.PluginHook;
import com.jnf.harness.spi.PluginHookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hook 注册表（动态组合）：插件 PRE/POST_TOOL_USE Hook 的宿主落点。
 * <p>可运行期注册/注销（插件卸载时逆序回收）。
 */
public class ToolHookRegistry implements ToolLifecycleHook {

    private static final Logger log = LoggerFactory.getLogger(ToolHookRegistry.class);

    public static final String PRE_TOOL_USE = "PRE_TOOL_USE";
    public static final String POST_TOOL_USE = "POST_TOOL_USE";

    private record Registration(String hookPoint, PluginHook hook) {
    }

    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();

    /** 注册 Hook，返回注销器。hookPoint 必须是 PRE_TOOL_USE / POST_TOOL_USE。 */
    public AutoCloseable register(String hookPoint, PluginHook hook) {
        if (!PRE_TOOL_USE.equals(hookPoint) && !POST_TOOL_USE.equals(hookPoint)) {
            throw new IllegalArgumentException("未知 hookPoint: " + hookPoint);
        }
        Registration registration = new Registration(hookPoint, hook);
        registrations.add(registration);
        return () -> registrations.remove(registration);
    }

    @Override
    public PluginHookResult beforeToolUse(String toolName, Map<String, Object> arguments) {
        return beforeToolUse(null, toolName, arguments);
    }

    @Override
    public PluginHookResult beforeToolUse(com.jnf.harness.model.ToolRunContext context,
                                          String toolName, Map<String, Object> arguments) {
        for (Registration registration : registrations) {
            if (!PRE_TOOL_USE.equals(registration.hookPoint())) {
                continue;
            }
            try {
                PluginHookResult verdict = context == null
                        ? registration.hook().evaluate(toolName, arguments)
                        : registration.hook().evaluate(context, toolName, arguments);
                if (verdict != null && verdict.intercepted()) {
                    return verdict;
                }
            } catch (Exception e) {
                log.warn("PRE_TOOL_USE Hook 异常（忽略）: {}", e.getMessage());
            }
        }
        return PluginHookResult.allow();
    }

    @Override
    public void afterToolUse(String toolName, Map<String, Object> arguments, ToolExecutionResult result) {
        afterToolUse(null, toolName, arguments, result);
    }

    @Override
    public void afterToolUse(com.jnf.harness.model.ToolRunContext context, String toolName,
                             Map<String, Object> arguments, ToolExecutionResult result) {
        for (Registration registration : registrations) {
            if (!POST_TOOL_USE.equals(registration.hookPoint())) {
                continue;
            }
            try {
                if (context == null) {
                    registration.hook().evaluate(toolName, arguments);
                } else {
                    registration.hook().evaluate(context, toolName, arguments);
                }
            } catch (Exception e) {
                log.warn("POST_TOOL_USE Hook 异常（忽略）: {}", e.getMessage());
            }
        }
    }
}
