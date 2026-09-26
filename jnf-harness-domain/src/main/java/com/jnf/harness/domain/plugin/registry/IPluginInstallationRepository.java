package com.jnf.harness.domain.plugin.registry;

import java.util.List;
import java.util.Optional;

/**
 * 插件安装仓储端口。
 */
public interface IPluginInstallationRepository {

    void upsert(PluginInstallationEntity entity);

    void updateStatus(String pluginId, String status);

    void delete(String pluginId);

    List<PluginInstallationEntity> findAll();

    Optional<PluginInstallationEntity> findById(String pluginId);
}
