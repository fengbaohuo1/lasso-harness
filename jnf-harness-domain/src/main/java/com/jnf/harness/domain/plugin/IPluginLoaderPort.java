package com.jnf.harness.domain.plugin;

import com.jnf.harness.spi.JavaHarnessPlugin;
import com.jnf.harness.spi.PluginManifest;

import java.nio.file.Path;

/**
 * 插件加载端口：从制品（JAR/目录）解析清单并实例化入口（infra 隔离 ClassLoader 实现）。
 */
public interface IPluginLoaderPort {

    /** 加载结果：清单 + 插件实例 + 资源（ClassLoaader，disable 时随插件释放）。 */
    record LoadedPlugin(PluginManifest manifest, JavaHarnessPlugin plugin, AutoCloseable resource) {
    }

    LoadedPlugin load(Path artifact);
}
