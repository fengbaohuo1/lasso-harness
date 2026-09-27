package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.registry.IPluginCatalogPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件目录扫描（P2）：Java 制品（目录/jar）+ Node 制品 + 坏制品跳过。
 */
class PluginCatalogServiceTest {

    @TempDir
    Path tempDir;

    private final PluginCatalogService service = new PluginCatalogService();

    @Test
    @DisplayName("识别 plugin.yaml 目录、plugin.yaml jar、package.json 目录；无关目录忽略")
    void scan_recognizes_artifacts() throws Exception {
        // Java 目录制品
        Path dirPlugin = tempDir.resolve("dir-plugin");
        Files.createDirectories(dirPlugin.resolve("META-INF"));
        Files.writeString(dirPlugin.resolve("META-INF").resolve("plugin.yaml"),
                "id: dir-plugin\nname: 目录制品\nversion: 1.2.3\nentrypoint: com.example.Plugin\n");

        // Java jar 制品
        Path jar = tempDir.resolve("jar-plugin.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/plugin.yaml"));
            out.write("id: jar-plugin\nname: Jar 制品\nversion: 0.9.0\n".getBytes());
            out.closeEntry();
        }

        // Node 制品
        Path nodePlugin = tempDir.resolve("node-plugin");
        Files.createDirectories(nodePlugin);
        Files.writeString(nodePlugin.resolve("package.json"),
                "{\"name\":\"node-plugin\",\"version\":\"2.0.0\",\"main\":\"main.js\"}");

        // 无关目录（既无 plugin.yaml 也无 package.json）
        Files.createDirectories(tempDir.resolve("junk"));

        List<IPluginCatalogPort.CatalogEntry> entries = service.scan(tempDir);

        assertEquals(3, entries.size());
        IPluginCatalogPort.CatalogEntry dir = byId(entries, "dir-plugin");
        assertEquals("1.2.3", dir.version());
        assertEquals("JAVA_NATIVE", dir.runtimeType());
        assertEquals("com.example.Plugin", dir.entrypoint());

        IPluginCatalogPort.CatalogEntry jarEntry = byId(entries, "jar-plugin");
        assertEquals("JAVA_NATIVE", jarEntry.runtimeType());
        assertTrue(jarEntry.sourcePath().endsWith("jar-plugin.jar"));

        IPluginCatalogPort.CatalogEntry node = byId(entries, "node-plugin");
        assertEquals("DSH_NODE_BRIDGE", node.runtimeType());
        assertEquals("main.js", node.entrypoint());
    }

    @Test
    @DisplayName("目录不存在 → 空清单")
    void scan_missing_dir() {
        assertEquals(0, service.scan(tempDir.resolve("nope")).size());
    }

    private static IPluginCatalogPort.CatalogEntry byId(List<IPluginCatalogPort.CatalogEntry> entries, String id) {
        return entries.stream().filter(e -> e.pluginId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少目录项: " + id));
    }
}
