package com.jnf.harness.infrastructure.adapter.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * 前端贡献清单读取（SD1）：{@code META-INF/plugin-ui.json}（jar 内资源 / 目录内文件）。
 * <p>plugin.yaml 可选 {@code ui:} 键覆盖默认路径；读不到 = 无 UI 贡献。
 */
public final class PluginUiManifestReader {

    public static final String DEFAULT_UI_PATH = "META-INF/plugin-ui.json";

    private PluginUiManifestReader() {
    }

    /** 读取插件制品的 UI 贡献清单原文（JSON）；无则 empty。 */
    public static Optional<String> read(Path artifact) {
        try {
            String uiPath = uiPath(artifact);
            if (Files.isDirectory(artifact)) {
                Path file = artifact.resolve(uiPath);
                return Files.exists(file)
                        ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                        : Optional.empty();
            }
            try (JarFile jar = new JarFile(artifact.toFile())) {
                var entry = jar.getJarEntry(uiPath);
                if (entry == null) {
                    return Optional.empty();
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String uiPath(Path artifact) throws IOException {
        String manifest = readPluginYaml(artifact);
        if (manifest != null) {
            Map<String, String> values = JavaPluginLoader.parseFlatYaml(manifest);
            String ui = values.get("ui");
            if (ui != null && !ui.isBlank()) {
                return ui.trim();
            }
        }
        return DEFAULT_UI_PATH;
    }

    private static String readPluginYaml(Path artifact) throws IOException {
        if (Files.isDirectory(artifact)) {
            Path file = artifact.resolve("META-INF").resolve("plugin.yaml");
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        }
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var entry = jar.getJarEntry("META-INF/plugin.yaml");
            if (entry == null) {
                return null;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
