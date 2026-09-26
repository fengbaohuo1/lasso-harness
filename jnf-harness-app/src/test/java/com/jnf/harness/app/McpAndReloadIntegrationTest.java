package com.jnf.harness.app;

import com.jnf.harness.app.fixture.JsonRpcServerFixture;
import com.jnf.harness.app.fixture.PluginArtifacts;
import com.jnf.harness.app.fixture.SampleFixturePlugin;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.domain.tool.mcp.McpServerService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.infrastructure.adapter.plugin.JsonRpcStdioBridge;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.PluginLifecycleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 通道 + 插件热重载验收（第 9 节收口）。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:mcptest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@EnabledIf(value = "com.jnf.harness.app.NodeBridgeIntegrationTest#nodeAvailable",
        disabledReason = "PATH 中无 node，跳过 stdio sidecar 验收")
class McpAndReloadIntegrationTest {

    @Autowired
    McpServerService mcpServerService;

    @Autowired
    PluginRegistryService registry;

    @Autowired
    ToolRegistry toolRegistry;

    private final ToolRunContext ctx = new ToolRunContext() {
        @Override
        public String agentId() {
            return "a";
        }

        @Override
        public String sessionId() {
            return "s";
        }

        @Override
        public String cwd() {
            return ".";
        }
    };

    @Test
    @DisplayName("MCP：连接 → mcp__ 限定名注册 → 调用 → 断开回收")
    void mcp_server_end_to_end(@TempDir Path tempDir) throws Exception {
        JsonRpcServerFixture.writeServer(tempDir.resolve("mcp-fixture"));
        JsonRpcStdioBridge bridge = new JsonRpcStdioBridge(
                tempDir.resolve("mcp-fixture"), List.of("node", "index.js"));

        McpServerService.McpServerHandle handle = mcpServerService.connect("fx", bridge);
        assertTrue(toolRegistry.lookup("mcp__fx__hello").isPresent(),
                "MCP 工具应以 mcp__<server>__<tool> 注册");

        ToolExecutionResult result = toolRegistry.lookup("mcp__fx__hello").orElseThrow()
                .execute(Map.of("who", "mcp"), ctx).get();
        assertEquals("hello mcp", result.content());

        handle.disconnect();
        assertTrue(toolRegistry.lookup("mcp__fx__hello").isEmpty(), "断开后工具应被回收");
    }

    @Test
    @DisplayName("热重载：reload = 逆序回收旧注册 + 重新激活")
    void plugin_hot_reload(@TempDir Path tempDir) throws Exception {
        Path artifact = PluginArtifacts.buildArtifact(tempDir, SampleFixturePlugin.class, "sample-fixture");
        registry.install(new PluginInstallationEntity("sample-fixture", "夹具", "1.0.0",
                null, artifact.toString(), null, null));
        registry.activate("sample-fixture");
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isPresent());

        registry.reload("sample-fixture");
        assertEquals(PluginLifecycleState.ACTIVE, registry.runtimeState("sample-fixture"));
        assertTrue(toolRegistry.lookup("plugin__sample-fixture__echo").isPresent(),
                "热重载后工具应重新注册");
    }
}
