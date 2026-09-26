package com.jnf.harness.app;

import com.jnf.harness.app.fixture.BadFixturePlugin;
import com.jnf.harness.app.fixture.SampleFixturePlugin;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.plugin.PluginHost;
import com.jnf.harness.domain.tool.service.ToolHookRegistry;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.spi.PluginLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件生命周期验收（第 9 节 DoD）：激活全注册口生效、卸载逆序回收、失败事务回滚。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:plugintest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class PluginLifecycleIntegrationTest {

    @Autowired
    PluginHost pluginHost;

    @Autowired
    ToolRegistry toolRegistry;

    @Autowired
    ToolHookRegistry hookRegistry;

    @Autowired
    SystemPromptAssembler promptAssembler;

    @Autowired
    com.jnf.harness.spi.PluginConfigStore configStore;

    /** 把测试夹具类打成"classes 目录"插件制品（含 plugin.yaml）。 */
    private Path buildArtifact(Path root, Class<?> pluginClass, String pluginId) throws Exception {
        return com.jnf.harness.app.fixture.PluginArtifacts.buildArtifact(root, pluginClass, pluginId);
    }

    @Test
    @DisplayName("激活：工具限定名注册 + 提示词 + Hook 生效；卸载：逆序回收全部 + Disposer 执行")
    void activate_and_disable_recycles_everything(@TempDir Path tempDir) throws Exception {
        Path artifact = buildArtifact(tempDir, SampleFixturePlugin.class, SampleFixturePlugin.PLUGIN_ID);
        Path marker = tempDir.resolve("disposed.marker");
        configStore.put("sample-fixture", "dispose-marker", marker.toString());

        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isEmpty(), "激活前不应有工具");

        pluginHost.activate(artifact);
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isPresent(),
                "激活后应有限定名工具 plugin__sample-fixture__echo");
        assertTrue(promptAssembler.assemble().contains("sample-fixture 插件提供 echo 工具"),
                "系统提示词应包含插件段落");
        assertTrue(hookRegistry.beforeToolUse("danger", Map.of()).intercepted(),
                "PRE Hook 应拦截 danger");
        assertEquals(PluginLifecycleState.ACTIVE, pluginHost.stateOf("sample-fixture"));

        pluginHost.disable("sample-fixture");
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isEmpty(), "卸载后工具应被回收");
        assertFalse(promptAssembler.assemble().contains("sample-fixture 插件提供 echo 工具"),
                "卸载后提示词段落应被回收");
        assertFalse(hookRegistry.beforeToolUse("danger", Map.of()).intercepted(),
                "卸载后 Hook 应被回收");
        assertTrue(Files.exists(marker), "Disposer 应执行（写标记文件）");
        assertEquals(PluginLifecycleState.DISABLED, pluginHost.stateOf("sample-fixture"));
    }

    @Test
    @DisplayName("失败回滚：configure 中途抛异常，已注册项全部回收（事务边界）")
    void activation_failure_rolls_back(@TempDir Path tempDir) throws Exception {
        Path artifact = buildArtifact(tempDir, BadFixturePlugin.class, BadFixturePlugin.PLUGIN_ID);

        assertThrows(IllegalStateException.class, () -> pluginHost.activate(artifact));
        assertTrue(toolRegistry.lookup("plugin__bad-fixture__ghost").isEmpty(),
                "失败后幽灵工具必须被回滚");
        assertEquals(PluginLifecycleState.FAILED, pluginHost.stateOf("bad-fixture"));
    }
}
