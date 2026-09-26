package com.jnf.harness.app;

import com.jnf.harness.app.fixture.PluginArtifacts;
import com.jnf.harness.app.fixture.SampleFixturePlugin;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.spi.PluginLifecycleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件管理链路验收：install 落库 → activate 生效 → disable/uninstall 状态一致。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:mgmttest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class PluginManagementIntegrationTest {

    @Autowired
    PluginRegistryService registry;

    @Autowired
    ToolRegistry toolRegistry;

    @Test
    @DisplayName("install→activate→disable→uninstall 全链路与状态一致")
    void plugin_management_lifecycle(@TempDir Path tempDir) throws Exception {
        Path artifact = PluginArtifacts.buildArtifact(tempDir, SampleFixturePlugin.class, "sample-fixture");

        // install：落库 REGISTERED
        PluginInstallationEntity record = registry.install(new PluginInstallationEntity(
                "sample-fixture", "夹具插件", "1.0.0", null, artifact.toString(), null, null));
        assertEquals(PluginLifecycleState.REGISTERED.name(), record.status());
        assertTrue(registry.get("sample-fixture").isPresent());

        // activate：工具生效 + 状态 ACTIVE
        registry.activate("sample-fixture");
        assertEquals(PluginLifecycleState.ACTIVE, registry.runtimeState("sample-fixture"));
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isPresent());

        // disable：回收 + 状态 DISABLED
        registry.disable("sample-fixture");
        assertEquals(PluginLifecycleState.DISABLED, registry.runtimeState("sample-fixture"));
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isEmpty());

        // uninstall：记录消失
        registry.uninstall("sample-fixture");
        assertTrue(registry.get("sample-fixture").isEmpty());

        // 制品缺失的 install 必须拒绝
        assertThrows(IllegalArgumentException.class, () -> registry.install(new PluginInstallationEntity(
                "nope", "x", "1", "JAVA_NATIVE", tempDir.resolve("missing").toString(), null, null)));
    }
}
