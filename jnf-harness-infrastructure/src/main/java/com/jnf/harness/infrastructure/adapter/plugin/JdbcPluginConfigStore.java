package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.registry.IPluginConfigRepository;
import com.jnf.harness.spi.PluginConfigStore;

import java.util.Optional;

/**
 * 插件配置存储（DB 持久化实现，P1 替换 InMemoryPluginConfigStore）。
 * <p>插件侧读写语义与内存版一致（get/put），值落 harness_plugin_config。
 */
public class JdbcPluginConfigStore implements PluginConfigStore {

    private final IPluginConfigRepository repository;

    public JdbcPluginConfigStore(IPluginConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> get(String pluginId, String key) {
        return repository.find(pluginId, key);
    }

    @Override
    public void put(String pluginId, String key, String value) {
        repository.upsert(pluginId, key, value);
    }
}
