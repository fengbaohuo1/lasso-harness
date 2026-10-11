package com.jnf.harness.app;

import com.jnf.harness.app.boot.PluginBootActivator;
import com.jnf.harness.domain.plugin.PluginHost;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.spi.PluginLifecycleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 多 jar 加载双通道验收：
 * classpath 发现（官方插件）→ 目录自动装载（真实 sample JAR）→ sourcePath 相对化
 * → 停用不复活 → 卸载摘工具。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.plugins.dir=target/test-plugins-multijar",
                "harness.events.dir=target/test-events-multijar",
                "spring.datasource.url=jdbc:h2:mem:dbpluginmj;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class PluginMultiJarIntegrationTest {

    private static final Path SAMPLE_JAR = Path.of(
            "../jnf-harness-sample-plugin/target/jnf-harness-sample-plugin-0.1.0-SNAPSHOT.jar");

    @Autowired
    PluginBootActivator bootActivator;

    @Autowired
    PluginRegistryService registry;

    @Autowired
    PluginHost pluginHost;

    @Autowired
    ToolRegistry toolRegistry;

    /** 清掉上次运行残留（启动自动装载会提前持锁旧 JAR，导致覆盖拷贝失败）。 */
    @org.junit.jupiter.api.BeforeAll
    static void cleanPluginsDir() throws Exception {
        Path dir = Path.of("target/test-plugins-multijar");
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    @DisplayName("classpath 发现：官方插件已激活、全局工具名可查")
    void classpath_plugins_active() {
        assertEquals(PluginLifecycleState.ACTIVE, pluginHost.stateOf("builtin-fs"));
        assertEquals(PluginLifecycleState.ACTIVE, pluginHost.stateOf("builtin-shell"));
        assertEquals(PluginLifecycleState.ACTIVE, pluginHost.stateOf("builtin-web"));
        assertEquals(PluginLifecycleState.ACTIVE, pluginHost.stateOf("essentials"));
        // toolNamespace=global：工具身份不随承载形态变化
        assertTrue(toolRegistry.lookup("fs_read").isPresent());
        assertTrue(toolRegistry.lookup("shell_execute").isPresent());
        assertTrue(toolRegistry.lookup("web_fetch").isPresent());
        assertTrue(toolRegistry.lookup("todo_write").isPresent(), "A3 essentials 插件提供 todo_write");
        assertTrue(toolRegistry.lookup("session_search").isPresent(), "A3 essentials 插件提供 session_search");
    }

    @Test
    @EnabledIf("sampleJarBuilt")
    @DisplayName("目录自动装载：真实 JAR 入目录 → 自动登记+激活（sourcePath 相对化）→ 停用不复活 → 卸载摘工具")
    void directory_jar_autoload_lifecycle() throws Exception {
        Path pluginsDir = Path.of("target/test-plugins-multijar");
        Files.createDirectories(pluginsDir);
        Path copied = pluginsDir.resolve("sample-tools-plugin.jar");
        Files.copy(SAMPLE_JAR, copied, StandardCopyOption.REPLACE_EXISTING);

        // 自动装载（模拟重启后 boot 行为）
        bootActivator.run(null);

        PluginInstallationEntity record = registry.get("sample-tools").orElseThrow();
        assertFalse(Path.of(record.sourcePath()).isAbsolute(),
                "sourcePath 应存相对文件名（跨机器可迁移），实际: " + record.sourcePath());
        assertEquals(PluginLifecycleState.ACTIVE, registry.runtimeState("sample-tools"));
        assertTrue(toolRegistry.list().stream().anyMatch(t -> t.name().startsWith("plugin__sample-tools__")),
                "sample 插件限定名工具应已注册");

        // 用户停用：再跑自动装载不复活
        registry.disable("sample-tools");
        bootActivator.run(null);
        assertEquals(PluginLifecycleState.DISABLED.name(),
                registry.get("sample-tools").orElseThrow().status());
        assertFalse(toolRegistry.list().stream().anyMatch(t -> t.name().startsWith("plugin__sample-tools__")));

        // 显式激活 + 卸载：工具随插件摘除
        registry.activate("sample-tools");
        assertNotNull(toolRegistry.list().stream()
                .filter(t -> t.name().startsWith("plugin__sample-tools__")).findFirst().orElse(null));
        registry.uninstall("sample-tools");
        assertFalse(toolRegistry.list().stream().anyMatch(t -> t.name().startsWith("plugin__sample-tools__")));
    }

    static boolean sampleJarBuilt() {
        return Files.exists(SAMPLE_JAR);
    }
}
