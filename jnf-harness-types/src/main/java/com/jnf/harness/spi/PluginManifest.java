package com.jnf.harness.spi;

import java.util.Objects;

/**
 * 插件元数据（来自 META-INF/plugin.yaml）。
 */
public record PluginManifest(String id, String name, String version,
                             String author, String description, String entrypoint) {

    public PluginManifest {
        Objects.requireNonNull(id, "id");
        version = version == null ? "0.0.0" : version;
        entrypoint = entrypoint == null ? "" : entrypoint;
    }
}
