package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.INodePluginLoaderPort;
import com.jnf.harness.domain.plugin.IPluginLoaderPort;
import com.jnf.harness.domain.plugin.RemoteToolsPlugin;
import com.jnf.harness.spi.PluginManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Node 插件加载器：校验入口 → 拉起 sidecar → 适配为 JavaHarnessPlugin（同构回收）。
 */
public class NodePluginLoader implements INodePluginLoaderPort {

    @Override
    public IPluginLoaderPort.LoadedPlugin load(Path pluginDir, String entrypoint, String pluginId) {
        if (!Files.isDirectory(pluginDir)) {
            throw new IllegalArgumentException("插件目录不存在: " + pluginDir);
        }
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId 必填");
        }
        // 入口限定插件目录内 .js/.mjs/.cjs（防注入）
        Path resolved = pluginDir.toAbsolutePath().normalize().resolve(entrypoint).normalize();
        if (!resolved.startsWith(pluginDir.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("入口必须位于插件目录内: " + entrypoint);
        }
        String fileName = resolved.getFileName().toString();
        if (!(fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs"))) {
            throw new IllegalArgumentException("入口必须是 .js/.mjs/.cjs: " + entrypoint);
        }
        JsonRpcStdioBridge bridge = new JsonRpcStdioBridge(pluginDir, List.of("node", resolved.toString()));
        bridge.start();
        try {
            PluginManifest manifest = new PluginManifest(pluginId, pluginId, "1.0.0",
                    "", "node bridge plugin", entrypoint);
            RemoteToolsPlugin plugin = new RemoteToolsPlugin(pluginId, bridge);
            return new IPluginLoaderPort.LoadedPlugin(manifest, plugin, bridge::stop);
        } catch (RuntimeException e) {
            bridge.stop();
            throw e;
        }
    }
}
