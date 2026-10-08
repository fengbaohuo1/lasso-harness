package com.jnf.harness.spi.host;

/**
 * 插件 HTTP 端点处理器（SD3）：插件经
 * {@code PluginContext.registerEndpoint(method, path, handler)} 注册，
 * 宿主在 {@code /api/harness/plugins/{pluginId}/api/{path}} 统一鉴权暴露。
 * <p>注册项纳入插件事务边界：停用/卸载时逆序回收，端点随之消失。
 */
@FunctionalInterface
public interface PluginHttpHandler {

    PluginHttpResponse handle(PluginHttpRequest request);
}
