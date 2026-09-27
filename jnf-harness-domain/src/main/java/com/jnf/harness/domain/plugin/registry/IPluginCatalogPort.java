package com.jnf.harness.domain.plugin.registry;

import java.nio.file.Path;
import java.util.List;

/**
 * 插件目录发现端口（P2）：扫描本地制品目录，输出「可安装」候选。
 */
public interface IPluginCatalogPort {

    record CatalogEntry(String pluginId, String displayName, String version, String runtimeType,
                        String sourcePath, String entrypoint, String description) {
    }

    /** 扫描 pluginsDir 下的 Java 制品（jar 或含 META-INF/plugin.yaml 的目录）与 Node Bridge 制品（含 package.json 的目录）。 */
    List<CatalogEntry> scan(Path pluginsDir);
}
