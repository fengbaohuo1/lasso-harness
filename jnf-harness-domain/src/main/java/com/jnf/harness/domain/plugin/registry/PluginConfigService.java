package com.jnf.harness.domain.plugin.registry;

import java.util.Map;
import java.util.Optional;

/**
 * 插件配置服务：按插件的 KV 读写（P1；供 REST 配套操作）。
 */
public class PluginConfigService {

    private final IPluginConfigRepository repository;

    public PluginConfigService(IPluginConfigRepository repository) {
        this.repository = repository;
    }

    /** 合并式保存：单键或整组（key 非空才写）。 */
    public void save(String pluginId, Map<String, String> config) {
        requirePluginId(pluginId);
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("config 不能为空");
        }
        config.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                repository.upsert(pluginId.trim(), key.trim(), value == null ? "" : value);
            }
        });
    }

    public Map<String, String> get(String pluginId) {
        requirePluginId(pluginId);
        return repository.findAll(pluginId.trim());
    }

    public Optional<String> getKey(String pluginId, String key) {
        requirePluginId(pluginId);
        return repository.find(pluginId.trim(), key);
    }

    public void deleteKey(String pluginId, String key) {
        requirePluginId(pluginId);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("configKey 必填");
        }
        repository.delete(pluginId.trim(), key.trim());
    }

    private static void requirePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId 必填");
        }
    }
}
