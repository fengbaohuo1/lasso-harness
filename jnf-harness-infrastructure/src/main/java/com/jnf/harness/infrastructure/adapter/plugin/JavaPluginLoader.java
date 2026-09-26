package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.IPluginLoaderPort;
import com.jnf.harness.spi.JavaHarnessPlugin;
import com.jnf.harness.spi.PluginManifest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

/**
 * Java 插件加载器：JAR / classes 目录均可加载。
 * <p>清单：{@code META-INF/plugin.yaml}（扁平 key: value）；
 * 入口：entrypoint 反射实例化，缺省走 ServiceLoader（SPI 文件）。
 * <p>一致性校验：pluginId() 必须与清单 id 一致。
 */
public class JavaPluginLoader implements IPluginLoaderPort {

    @Override
    public LoadedPlugin load(Path artifact) {
        try {
            Map<String, String> manifestValues = readManifest(artifact);
            String id = required(manifestValues, "id");
            String entrypoint = manifestValues.getOrDefault("entrypoint", "");
            PluginManifest manifest = new PluginManifest(id,
                    manifestValues.getOrDefault("name", id),
                    manifestValues.getOrDefault("version", "0.0.0"),
                    manifestValues.getOrDefault("author", ""),
                    manifestValues.getOrDefault("description", ""),
                    entrypoint);

            URL url = Files.isDirectory(artifact) ? artifact.toUri().toURL()
                    : artifact.toUri().toURL();
            PluginClassLoader classLoader = new PluginClassLoader(new URL[]{url},
                    JavaPluginLoader.class.getClassLoader());

            JavaHarnessPlugin plugin = instantiate(entrypoint, classLoader);
            if (!manifest.id().equals(plugin.pluginId())) {
                closeQuietly(classLoader);
                throw new IllegalStateException("pluginId 不一致: 清单=" + manifest.id()
                        + " 实现=" + plugin.pluginId());
            }
            return new LoadedPlugin(manifest, plugin, classLoader);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("插件加载失败: " + artifact + ": " + e.getMessage(), e);
        }
    }

    private JavaHarnessPlugin instantiate(String entrypoint, PluginClassLoader classLoader)
            throws Exception {
        if (!entrypoint.isBlank()) {
            Class<?> type = classLoader.loadClass(entrypoint.trim());
            return (JavaHarnessPlugin) type.getDeclaredConstructor().newInstance();
        }
        for (JavaHarnessPlugin candidate : ServiceLoader.load(JavaHarnessPlugin.class, classLoader)) {
            return candidate;
        }
        throw new IllegalStateException("未找到插件入口（entrypoint 或 META-INF/services 均缺失）");
    }

    /** 读取 META-INF/plugin.yaml（jar 或目录），解析扁平 key: value。 */
    private Map<String, String> readManifest(Path artifact) throws IOException {
        String content;
        if (Files.isDirectory(artifact)) {
            Path manifestFile = artifact.resolve("META-INF").resolve("plugin.yaml");
            if (!Files.exists(manifestFile)) {
                throw new IllegalStateException("缺少 META-INF/plugin.yaml");
            }
            content = Files.readString(manifestFile, StandardCharsets.UTF_8);
        } else {
            try (JarFile jar = new JarFile(artifact.toFile())) {
                var entry = jar.getJarEntry("META-INF/plugin.yaml");
                if (entry == null) {
                    throw new IllegalStateException("缺少 META-INF/plugin.yaml");
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        Map<String, String> values = new HashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                values.put(trimmed.substring(0, colon).trim(),
                        trimmed.substring(colon + 1).trim().replaceFirst("^['\"]|['\"]$", ""));
            }
        }
        return values;
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("plugin.yaml 缺少必填字段: " + key);
        }
        return value;
    }

    private void closeQuietly(PluginClassLoader classLoader) {
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // 尽力关闭
        }
    }
}
