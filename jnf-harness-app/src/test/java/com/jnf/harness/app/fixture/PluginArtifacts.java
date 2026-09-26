package com.jnf.harness.app.fixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * 测试夹具工具：把夹具插件类打成"classes 目录"制品（含 META-INF/plugin.yaml）。
 */
public final class PluginArtifacts {

    private PluginArtifacts() {
    }

    public static Path buildArtifact(Path root, Class<?> pluginClass, String pluginId) throws Exception {
        Path artifact = root.resolve(pluginId);
        Path classesRoot = Path.of(pluginClass.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        String packagePath = pluginClass.getPackageName().replace('.', '/');
        Path packageDir = classesRoot.resolve(packagePath);
        Path targetDir = artifact.resolve(packagePath);
        Files.createDirectories(targetDir);
        try (Stream<Path> files = Files.list(packageDir)) {
            for (Path file : files.toList()) {
                if (file.getFileName().toString().startsWith(pluginClass.getSimpleName())
                        && file.toString().endsWith(".class")) {
                    Files.copy(file, targetDir.resolve(file.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path meta = artifact.resolve("META-INF");
        Files.createDirectories(meta);
        Files.writeString(meta.resolve("plugin.yaml"),
                "id: " + pluginId + "\nname: " + pluginId + "\nversion: 1.0.0\n"
                        + "entrypoint: " + pluginClass.getName() + "\n");
        return artifact;
    }
}
