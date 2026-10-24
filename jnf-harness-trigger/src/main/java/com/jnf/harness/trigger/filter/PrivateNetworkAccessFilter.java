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
 * <p>注意：这里**只**补 PNA 头，不要手工回显 {@code Access-Control-Allow-Origin}——
 * 一旦响应提前带上该头，Spring {@code DefaultCorsProcessor} 会认为 CORS 已处理而整体跳过，
 * 导致 JSON POST 的预检响应缺少 {@code Access-Control-Allow-Methods/Headers} 被浏览器拦截。
 * CORS 头统一交给 {@link WebMvcConfig#addCorsMappings} 处理。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateNetworkAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Private-Network", "true");
        filterChain.doFilter(request, response);
    }
}
