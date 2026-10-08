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
            return dirOfLocation(source.getLocation().toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 由 code source location 串解析制品目录（纯函数，便于覆盖各种 classloader URL 形态）。 */
    static Optional<Path> dirOfLocation(String location) {
        try {
            String path = location;
            // 形态：Spring Boot 3.2+ nested —— jar:nested:/C:/dist/app.jar/!BOOT-INF/classes!/
            //      旧式 fat jar —— jar:file:/C:/dist/app.jar!/BOOT-INF/classes!/
            //      开发态 classes —— file:/C:/proj/target/classes/
            for (String prefix : new String[]{"jar:", "nested:", "file:"}) {
                while (path.startsWith(prefix)) {
                    path = path.substring(prefix.length());
                }
            }
            int bang = path.indexOf('!');
            if (bang >= 0) {
                path = path.substring(0, bang);
            }
            while (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
            Path locationPath = Path.of(path);
            // fat jar → jar 所在目录；classes 目录 → 其上级（target/）
            return Optional.ofNullable(locationPath.getParent());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
