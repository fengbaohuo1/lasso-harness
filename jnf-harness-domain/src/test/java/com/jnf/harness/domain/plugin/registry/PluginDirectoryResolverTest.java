package com.jnf.harness.domain.plugin.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 插件目录解析：各种 classloader location 形态都要拿到 jar 同级目录。
 * （Spring Boot 3.2+ nested URL 曾让桌面内核找不到 plugins/——实机踩坑回归。）
 */
class PluginDirectoryResolverTest {

    @Test
    @DisplayName("Spring Boot 3.2+ nested URL → jar 所在目录")
    void nested_url() {
        Optional<Path> dir = PluginDirectoryResolver.dirOfLocation(
                "jar:nested:/C:/dist/jnf-harness-app.jar/!BOOT-INF/classes!/");
        assertEquals("C:/dist", dir.orElseThrow().toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("旧式 fat jar URL → jar 所在目录")
    void legacy_jar_url() {
        Optional<Path> dir = PluginDirectoryResolver.dirOfLocation(
                "jar:file:/C:/dist/jnf-harness-app.jar!/BOOT-INF/classes!/");
        assertEquals("C:/dist", dir.orElseThrow().toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("开发态 classes 目录 → 其上级（target/）")
    void classes_dir() {
        Optional<Path> dir = PluginDirectoryResolver.dirOfLocation(
                "file:/D:/study/dsh-java/jnf-harness/jnf-harness-app/target/classes/");
        assertEquals("D:/study/dsh-java/jnf-harness/jnf-harness-app/target",
                dir.orElseThrow().toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("垃圾输入安全降级为 empty")
    void garbage_degrades() {
        assertTrue(PluginDirectoryResolver.dirOfLocation("opaque:???").isEmpty());
    }
}
