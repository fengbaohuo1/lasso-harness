package com.jnf.harness.domain.plugin;

import com.jnf.harness.spi.PluginConfigStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存插件配置存储（DB 实现随后续节替换；getConfig 语义一致）。
 */
public class InMemoryPluginConfigStore implements PluginConfigStore {

    private final Map<String, Map<String, String>> byPlugin = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String pluginId, String key) {
        return Optional.ofNullable(byPlugin.getOrDefault(pluginId, Map.of()).get(key));
    }

    @Override
    public void put(String pluginId, String key, String value) {
        byPlugin.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>()).put(key, value);
    }
}
