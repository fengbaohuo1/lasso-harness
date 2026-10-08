package com.jnf.harness.domain.plugin.registry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;

/**
 * 插件目录解析（P3）：
 * <ol>
 *   <li>显式配置 {@code harness.plugins.dir}；</li>
 *   <li>内核 jar 同级 {@code plugins/}（多 jar 发布布局：dist/{app.jar, plugins/}）；</li>
 *   <li>工作目录 {@code ./plugins}（开发布局兜底）。</li>
 * </ol>
 */
public final class PluginDirectoryResolver {

    private PluginDirectoryResolver() {
    }

    public static Path resolve(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Optional<Path> jarAdjacent = selfJarDir().map(dir -> dir.resolve("plugins"));
        if (jarAdjacent.isPresent() && Files.isDirectory(jarAdjacent.get())) {
            return jarAdjacent.get().toAbsolutePath().normalize();
        }
        return Path.of("plugins").toAbsolutePath().normalize();
    }

    /** 内核自身制品所在目录（fat jar 同级 / 开发态 classes 目录上级）；失败返回 empty。 */
    public static Optional<Path> selfJarDir() {
        try {
            CodeSource source = PluginDirectoryResolver.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return Optional.empty();
            }
            String raw = source.getLocation().toString();
            // 形如 jar:file:/C:/dist/jnf-harness-app.jar!/BOOT-INF/classes!/ 或 file:/.../target/classes/
            String path = raw;
            int bang = path.indexOf('!');
            if (bang >= 0) {
                path = path.substring(0, bang);
            }
            if (path.startsWith("jar:")) {
                path = path.substring(4);
            }
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
            while (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
            Path location = Path.of(path);
            // fat jar → jar 所在目录；classes 目录 → 其上级（target/）
            return Optional.ofNullable(location.getParent());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
