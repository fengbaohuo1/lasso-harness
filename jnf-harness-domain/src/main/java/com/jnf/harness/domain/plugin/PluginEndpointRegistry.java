package com.jnf.harness.domain.plugin;

import com.jnf.harness.spi.host.PluginHttpHandler;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件 HTTP 端点注册表（SD3）：key = pluginId + 方法 + 路径。
 * <p>注册/回收随 {@link HostPluginContext} 事务边界（停用/卸载逆序回收，端点随之消失）。
 */
public class PluginEndpointRegistry {

    private record Key(String pluginId, String method, String path) {
    }

    private final Map<Key, PluginHttpHandler> endpoints = new ConcurrentHashMap<>();

    /** 注册端点；返回注销器。路径/方法做规范化与防御（不带宿主前缀、拒绝穿越）。 */
    public Runnable register(String pluginId, String method, String path, PluginHttpHandler handler) {
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(path);
        Key key = new Key(pluginId, normalizedMethod, normalizedPath);
        if (endpoints.containsKey(key)) {
            throw new IllegalStateException("插件端点已存在: " + normalizedMethod + " " + normalizedPath);
        }
        endpoints.put(key, handler);
        return () -> endpoints.remove(key, handler);
    }

    public Optional<PluginHttpHandler> lookup(String pluginId, String method, String path) {
        return Optional.ofNullable(endpoints.get(new Key(pluginId, normalizeMethod(method), normalizePath(path))));
    }

    /** 该路径是否被占用（用于 405/404 区分）。 */
    public boolean pathExists(String pluginId, String path) {
        String normalizedPath = normalizePath(path);
        return endpoints.keySet().stream()
                .anyMatch(key -> key.pluginId().equals(pluginId) && key.path().equals(normalizedPath));
    }

    /** 端点项（P8-2 贡献面可视化）。 */
    public record EndpointRoute(String method, String path) {
    }

    /** 该插件当前注册的端点清单（方法+路径，按路径/方法排序）。 */
    public List<EndpointRoute> list(String pluginId) {
        return endpoints.keySet().stream()
                .filter(key -> key.pluginId().equals(pluginId))
                .map(key -> new EndpointRoute(key.method(), key.path()))
                .sorted(java.util.Comparator.comparing(EndpointRoute::path)
                        .thenComparing(EndpointRoute::method))
                .toList();
    }

    public int size() {
        return endpoints.size();
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("HTTP 方法不能为空");
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("端点路径不能为空");
        }
        if (value.contains("..") || value.contains("//") || value.contains("\\")) {
            throw new IllegalArgumentException("端点路径非法: " + path);
        }
        return value;
    }
}
