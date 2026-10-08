package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.plugin.PluginEndpointRegistry;
import com.jnf.harness.spi.host.PluginHttpHandler;
import com.jnf.harness.spi.host.PluginHttpRequest;
import com.jnf.harness.spi.host.PluginHttpResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 插件 REST 端点宿主（SD3，前后端插件统筹）：
 * {@code /api/harness/plugins/{pluginId}/api/{path}} —— 鉴权沿用全局 X-API-Key 拦截器；
 * 统一信封 {@code {code,info,data}}；注册项随插件停用/卸载回收（404 业务码）。
 */
@RestController
@RequestMapping("/api/harness/plugins/{pluginId}/api")
public class PluginApiController {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final PluginEndpointRegistry endpoints;

    public PluginApiController(PluginEndpointRegistry endpoints) {
        this.endpoints = endpoints;
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE})
    public Response<Object> invoke(@PathVariable String pluginId, HttpServletRequest request,
                                   @RequestBody(required = false) String body) {
        String method = request.getMethod().toUpperCase(java.util.Locale.ROOT);
        String subPath = subPath(request);
        Optional<PluginHttpHandler> handler = endpoints.lookup(pluginId, method, subPath);
        if (handler.isEmpty()) {
            if (endpoints.pathExists(pluginId, subPath)) {
                return Response.invalidArgument("方法不允许: " + method + " " + subPath);
            }
            return Response.invalidArgument("插件端点不存在: " + method + " /" + subPath);
        }
        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                query.put(key, values.length > 0 ? values[0] : ""));
        try {
            PluginHttpResponse response = handler.get()
                    .handle(new PluginHttpRequest(method, subPath, query, body));
            if (response.status() >= 400) {
                return response.status() >= 500
                        ? Response.internalError(String.valueOf(response.body()))
                        : Response.invalidArgument(String.valueOf(response.body()));
            }
            return Response.success(response.body());
        } catch (Exception e) {
            return Response.internalError("插件端点执行失败: " + e.getMessage());
        }
    }

    /** 取端点内相对路径（去掉宿主前缀 /api/harness/plugins/{id}/api/）。 */
    private String subPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int pluginsAt = uri.indexOf("/plugins/");
        String tail = pluginsAt >= 0 ? uri.substring(pluginsAt + "/plugins/".length()) : uri;
        // tail 形如 "<pluginId>/api/<sub>"
        int marker = tail.indexOf("/api/");
        String sub = marker >= 0 ? tail.substring(marker + "/api/".length()) : "";
        while (sub.endsWith("/")) {
            sub = sub.substring(0, sub.length() - 1);
        }
        return sub;
    }
}
