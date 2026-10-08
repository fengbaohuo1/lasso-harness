package com.jnf.harness.spi;

import java.util.Objects;

/**
 * 插件元数据（来自 META-INF/plugin.yaml）。
 *
 * @param sdkVersion    插件要求的 SDK 版本（缺省 1；高于宿主支持值时加载报错）
 * @param toolNamespace 工具命名空间：{@code plugin}（默认，工具限定名 plugin__id__name）
 *                      或 {@code global}（官方插件保持全局工具名，如 fs_read；宿主保证全局唯一）
 */
public record PluginManifest(String id, String name, String version,
                             String author, String description, String entrypoint,
                             int sdkVersion, String toolNamespace) {

    /** 工具限定命名（默认）。 */
    public static final String NAMESPACE_PLUGIN = "plugin";
    /** 全局工具命名（官方插件；工具身份不随承载形态变化）。 */
    public static final String NAMESPACE_GLOBAL = "global";

    /** 兼容旧调用面（无 sdkVersion/工具命名空间，按缺省值）。 */
    public PluginManifest(String id, String name, String version,
                          String author, String description, String entrypoint) {
        this(id, name, version, author, description, entrypoint, 1, NAMESPACE_PLUGIN);
    }

    public PluginManifest {
        Objects.requireNonNull(id, "id");
        version = version == null ? "0.0.0" : version;
        entrypoint = entrypoint == null ? "" : entrypoint;
        toolNamespace = toolNamespace == null || toolNamespace.isBlank() ? NAMESPACE_PLUGIN : toolNamespace;
    }

    /** 工具是否以全局名注册（不加 plugin__ 限定前缀）。 */
    public boolean globalToolNames() {
        return NAMESPACE_GLOBAL.equals(toolNamespace);
    }
}
