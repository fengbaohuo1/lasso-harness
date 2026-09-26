package com.jnf.harness.spi;

import java.util.Optional;

/**
 * 插件配置存储：按 pluginId 隔离的 KV 读写（宿主可提供内存/数据库实现）。
 */
public interface PluginConfigStore {

    Optional<String> get(String pluginId, String key);

    void put(String pluginId, String key, String value);
}
