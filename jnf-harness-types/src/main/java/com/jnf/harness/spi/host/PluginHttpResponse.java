package com.jnf.harness.spi.host;

/**
 * 插件 HTTP 端点响应。
 *
 * @param status HTTP 状态码（&lt;400 视为成功，宿主包统一信封）
 * @param body   响应体（宿主用 JSON 序列化）
 */
public record PluginHttpResponse(int status, Object body) {

    public static PluginHttpResponse ok(Object body) {
        return new PluginHttpResponse(200, body);
    }

    public static PluginHttpResponse error(int status, String message) {
        return new PluginHttpResponse(status, message);
    }
}
