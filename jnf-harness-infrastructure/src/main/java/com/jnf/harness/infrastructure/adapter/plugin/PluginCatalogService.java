package com.jnf.harness.infrastructure.adapter.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.plugin.registry.IPluginCatalogPort;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 插件目录扫描（P2）：
 * <ul>
 *   <li>Java 制品：{@code *.jar}（内含 META-INF/plugin.yaml）或含 {@code META-INF/plugin.yaml} 的目录；</li>
 *   <li>Node Bridge 制品：含 {@code package.json} 的目录（入口取 main，默认 index.js）。</li>
 * </ul>
 * 目录只读扫描，不落库；安装由 {@code /plugins/install} 登记。
 */
public class PluginCatalogService implements IPluginCatalogPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<CatalogEntry> scan(Path pluginsDir) {
        List<CatalogEntry> entries = new ArrayList<>();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return entries;
        }
        try (Stream<Path> children = Files.list(pluginsDir)) {
            children.sorted(Comparator.comparing(path -> path.getFileName().toString())).forEach(child -> {
                try {
                    CatalogEntry entry = Files.isDirectory(child) ? fromDirectory(child) : fromJar(child);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (IOException | RuntimeException e) {
                    // 单个制品损坏不影响清单其余项
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("插件目录扫描失败: " + pluginsDir + ": " + e.getMessage(), e);
        }
        entries.sort(Comparator.comparing(CatalogEntry::pluginId));
        return entries;
    }

    private CatalogEntry fromDirectory(Path dir) throws IOException {
        Path yaml = dir.resolve("META-INF").resolve("plugin.yaml");
        if (Files.exists(yaml)) {
            Map<String, String> manifest = parseFlatYaml(Files.readString(yaml, StandardCharsets.UTF_8));
            return javaEntry(requiredId(manifest, dir), manifest, dir.toString());
        }
        Path pkg = dir.resolve("package.json");
        if (Files.exists(pkg)) {
            JsonNode root = MAPPER.readTree(Files.readAllBytes(pkg));
            String name = root.path("name").asText("");
            if (name.isBlank()) {
                return null;
            }
            String entrypoint = root.path("main").asText("index.js");
            return new CatalogEntry(name, root.path("description").asText(name),
                    root.path("version").asText("0.0.0"),
                    PluginRegistryService.RUNTIME_NODE_BRIDGE,
                    dir.toString(), entrypoint, "node bridge plugin");
        }
        return null;
    }

    private CatalogEntry fromJar(Path jarPath) throws IOException {
        if (!jarPath.getFileName().toString().endsWith(".jar")) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry("META-INF/plugin.yaml");
            if (entry == null) {
                return null;
            }
            Map<String, String> manifest;
            try (InputStream in = jar.getInputStream(entry)) {
                manifest = parseFlatYaml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            return javaEntry(requiredId(manifest, jarPath), manifest, jarPath.toString());
        }
    }

    private static CatalogEntry javaEntry(String pluginId, Map<String, String> manifest, String sourcePath) {
        return new CatalogEntry(pluginId,
                manifest.getOrDefault("name", pluginId),
                manifest.getOrDefault("version", "0.0.0"),
                PluginRegistryService.RUNTIME_JAVA_NATIVE,
                sourcePath,
                manifest.getOrDefault("entrypoint", ""),
                manifest.getOrDefault("description", ""));
    }

    private static String requiredId(Map<String, String> manifest, Path artifact) {
        String id = manifest.getOrDefault("id", "").trim();
        if (id.isBlank()) {
            throw new IllegalStateException("plugin.yaml 缺 id: " + artifact);
        }
        return id;
    }

    /** 扁平 key: value 解析（与 JavaPluginLoader 清单格式一致）。 */
    private static Map<String, String> parseFlatYaml(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : content.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String value = trimmed.substring(colon + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(trimmed.substring(0, colon).trim(), value);
        }
        return values;
    }
}
