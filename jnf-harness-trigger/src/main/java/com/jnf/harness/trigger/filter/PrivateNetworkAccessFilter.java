package com.jnf.harness.trigger.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 私网访问（PNA）响应头：WebView2/Chromium 从非本机源（如 http://tauri.localhost）
 * 请求 127.0.0.1 时要求 {@code Access-Control-Allow-Private-Network: true}，
 * 否则内核直接拦截 fetch（外网 curl/PowerShell 不受影响，极具迷惑性）。
 * <p>同时兜底回显 CORS 来源，保证桌面壳内 window.fetch 可用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateNetworkAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Private-Network", "true");
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !response.containsHeader("Access-Control-Allow-Origin")) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.addHeader("Vary", "Origin");
        }
        filterChain.doFilter(request, response);
    }
}
