package com.jnf.harness.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * API Key 鉴权拦截器：X-API-Key 或 Authorization: Bearer。
 * <p>keys 为空 → 全放行（本地开发模式）；白名单：静态资源、actuator、OPTIONS 预检。
 */
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final List<String> apiKeys;

    public ApiKeyAuthInterceptor(List<String> apiKeys) {
        this.apiKeys = apiKeys == null ? List.of() : apiKeys.stream()
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .toList();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || apiKeys.isEmpty()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/.well-known")
                || path.equals("/") || path.startsWith("/index")
                || path.startsWith("/app.") || path.startsWith("/lib/") || path.startsWith("/favicon")) {
            return true;
        }
        String provided = request.getHeader("X-API-Key");
        if (provided == null || provided.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                provided = authorization.substring("Bearer ".length()).trim();
            }
        }
        if (provided != null && apiKeys.contains(provided.trim())) {
            return true;
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
        return false;
    }
}
