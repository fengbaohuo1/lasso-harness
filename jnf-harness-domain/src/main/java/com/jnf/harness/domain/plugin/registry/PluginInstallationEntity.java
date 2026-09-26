package com.jnf.harness.domain.plugin.registry;

/**
 * 插件安装记录（持久化实体）。
 */
public record PluginInstallationEntity(String pluginId, String displayName, String pluginVersion,
                                       String runtimeType, String sourcePath, String entrypoint,
                                       String status) {
}
