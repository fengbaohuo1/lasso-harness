package com.jnf.harness.domain.plugin.registry;

import java.util.Map;
import java.util.Optional;

/**
 * 插件配置仓储端口（持久化；layer 固定 default，由 infra 实现定死）。
 */
public interface IPluginConfigRepository {

    void upsert(String pluginId, String configKey, String configValue);

    void delete(String pluginId, String configKey);

    Optional<String> find(String pluginId, String configKey);

    Map<String, String> findAll(String pluginId);
}
