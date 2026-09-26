package com.jnf.harness.app;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实插件 JAR 验收：从 sample-plugin 模块产物安装（JarFile 清单解析 + 隔离加载）。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:sampletest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class SamplePluginJarIntegrationTest {

    static final Path SAMPLE_JAR = Path.of("..", "jnf-harness-sample-plugin", "target",
            "jnf-harness-sample-plugin-0.1.0-SNAPSHOT.jar");

    static boolean sampleJarBuilt() {
        return Files.exists(SAMPLE_JAR);
    }

    @Autowired
    PluginRegistryService registry;

    @Autowired
    ToolRegistry toolRegistry;

    @Test
    @DisplayName("sample 插件 JAR：安装→激活→工具可用→卸载回收")
    @EnabledIf(value = "com.jnf.harness.app.SamplePluginJarIntegrationTest#sampleJarBuilt",
            disabledReason = "sample 插件 JAR 未构建（单模块构建时跳过）")
    void install_real_plugin_jar() {
        registry.install(new PluginInstallationEntity("sample-tools", "示例插件", "1.0.0",
                null, SAMPLE_JAR.toAbsolutePath().toString(), null, null));
        registry.activate("sample-tools");

        assertTrue(toolRegistry.lookup("plugin__sample-tools__weather_query").isPresent());
        assertTrue(toolRegistry.lookup("plugin__sample-tools__file_summary").isPresent());
        assertTrue(registry.runtimeState("sample-tools") == PluginLifecycleState.ACTIVE);

        registry.uninstall("sample-tools");
        assertTrue(toolRegistry.lookup("plugin__sample-tools__weather_query").isEmpty());
    }
}
