package com.jnf.harness.plugin.sandbox;

import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginHook;
import com.jnf.harness.spi.PluginHookResult;
import com.jnf.harness.spi.host.PluginHttpRequest;
import com.jnf.harness.spi.host.PluginHttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙箱插件（P9-2）：三档权限模式的运行期强制与管理面。
 * <ul>
 *   <li>PRE_TOOL_USE Hook：按会话模式拦截（READ_ONLY 拦写/shell、WORKSPACE_WRITE 拦越界/越逸、FULL 全放）；</li>
 *   <li>端点 {@code GET/PUT/POST mode}：查询/设置会话或默认权限模式；</li>
 *   <li>配置 {@code mode}：默认档（缺省 WORKSPACE_WRITE）。</li>
 * </ul>
 */
public class SandboxPlugin extends AbstractHarnessPlugin {

    private SandboxPolicy policy;

    public SandboxPlugin() {
        super("sandbox");
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        policy = new SandboxPolicy(getConfig("mode", SandboxPolicy.WORKSPACE_WRITE));
        // 会话感知 Hook：必须覆写带 ToolRunContext 的形态（模式按会话裁）
        context.registerHook("PRE_TOOL_USE", new PluginHook() {
            @Override
            public PluginHookResult evaluate(String toolName, Map<String, Object> arguments) {
                return policy.evaluate(null, toolName, arguments);
            }

            @Override
            public PluginHookResult evaluate(ToolRunContext toolContext, String toolName,
                                             Map<String, Object> arguments) {
                return policy.evaluate(toolContext, toolName, arguments);
            }
        });
        context.registerEndpoint("GET", "mode", this::getMode);
        context.registerEndpoint("PUT", "mode", this::setMode);
        context.registerEndpoint("POST", "mode", this::setMode);
    }

    private PluginHttpResponse getMode(PluginHttpRequest request) {
        String sessionId = request.query().getOrDefault("sessionId", "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("mode", policy.modeOf(sessionId));
        body.put("defaultMode", policy.defaultMode());
        return new PluginHttpResponse(200, body);
    }

    private PluginHttpResponse setMode(PluginHttpRequest request) {
        String sessionId = field(request.bodyJson(), "sessionId");
        String mode = field(request.bodyJson(), "mode");
        if (mode == null || mode.isBlank()) {
            return new PluginHttpResponse(400, Map.of("error", "mode 必填（READ_ONLY/WORKSPACE_WRITE/DANGER_FULL_ACCESS）"));
        }
        if (!SandboxPolicy.MODES.contains(SandboxPolicy.normalize(mode))) {
            return new PluginHttpResponse(400, Map.of("error", "mode 只能是 READ_ONLY/WORKSPACE_WRITE/DANGER_FULL_ACCESS"));
        }
        policy.setMode(sessionId, mode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId == null ? "" : sessionId.trim());
        body.put("mode", policy.modeOf(sessionId));
        body.put("defaultMode", policy.defaultMode());
        return new PluginHttpResponse(200, body);
    }

    /** 极简 JSON 字符串字段提取（插件只依赖 SDK 契约，不引 JSON 库）。 */
    private static String field(String json, String key) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
