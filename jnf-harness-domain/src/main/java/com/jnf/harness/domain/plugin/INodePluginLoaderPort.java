package com.jnf.harness.domain.plugin;

import java.nio.file.Path;

/**
 * Node 插件加载端口（sidecar 进程 + JSON-RPC）。
 */
public interface INodePluginLoaderPort {

    /** 加载 Node 插件目录（校验入口后拉起 sidecar，返回与 Java 插件同构的加载结果）。 */
    IPluginLoaderPort.LoadedPlugin load(Path pluginDir, String entrypoint, String pluginId);
}
