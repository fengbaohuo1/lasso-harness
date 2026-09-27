package com.jnf.harness.domain.plugin.registry;

/**
 * 插件运行绑定记录（harness_plugin_runtime_binding；P3：Node Bridge 激活状态落库）。
 */
public record PluginRuntimeBindingEntity(String pluginId, String packageType, String manifestPath,
                                         String workingDirectory, String runtimeCommand,
                                         boolean bridgeReady, String message) {
}
