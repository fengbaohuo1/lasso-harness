package com.jnf.harness.app;

import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.PluginLifecycleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Node Bridge 验收：真拉起 sidecar 进程，JSON-RPC tools/list → 限定名注册 → tools/call → 逆序回收。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:nodetest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@EnabledIf(value = "com.jnf.harness.app.NodeBridgeIntegrationTest#nodeAvailable",
        disabledReason = "PATH 中无 node，跳过 Node Bridge 验收")
class NodeBridgeIntegrationTest {

    static boolean nodeAvailable() {
        try {
            Process process = new ProcessBuilder("node", "--version").start();
            return process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 最小 JSON-RPC sidecar 夹具：initialize / tools/list / tools/call。 */
    private static final String FIXTURE_JS = """
            const readline = require('readline');
            const rl = readline.createInterface({ input: process.stdin });
            rl.on('line', (line) => {
              if (!line.trim()) return;
              let req; try { req = JSON.parse(line); } catch (e) { return; }
              const respond = (result) => process.stdout.write(
                  JSON.stringify({ jsonrpc: '2.0', id: req.id, result: result }) + '\\n');
              if (req.method === 'initialize') {
                respond({ serverInfo: { name: 'node-fixture' } });
              } else if (req.method === 'tools/list') {
                respond({ tools: [{ name: 'hello', description: '打招呼',
                  parameters: { type: 'object', properties: { who: { type: 'string' } } } }] });
              } else if (req.method === 'tools/call') {
                const who = (req.params.arguments && req.params.arguments.who) || 'world';
                respond({ content: [{ type: 'text', text: 'hello ' + who }] });
              }
            });
            """;

    @Autowired
    PluginRegistryService registry;

    @Autowired
    ToolRegistry toolRegistry;

    @Test
    @DisplayName("sidecar 全链路：list 注册限定名工具、call 转发、disable 回收")
    void node_bridge_end_to_end(@TempDir Path tempDir) throws Exception {
        Path pluginDir = tempDir.resolve("node-fixture");
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("index.js"), FIXTURE_JS);

        registry.install(new PluginInstallationEntity("node-fixture", "Node 夹具", "1.0.0",
                PluginRegistryService.RUNTIME_NODE_BRIDGE, pluginDir.toString(), "index.js", null));
        registry.activate("node-fixture");

        assertEquals(PluginLifecycleState.ACTIVE, registry.runtimeState("node-fixture"));
        assertTrue(toolRegistry.lookup("plugin__node-fixture__hello").isPresent(),
                "sidecar 工具应以限定名注册");

        ToolExecutionResult result = toolRegistry.lookup("plugin__node-fixture__hello")
                .orElseThrow()
                .execute(Map.of("who", "jnf"), new ToolRunContext() {
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
                }).get();
        assertTrue(result.success());
        assertEquals("hello jnf", result.content());

        registry.disable("node-fixture");
        assertTrue(toolRegistry.lookup("plugin__node-fixture__hello").isEmpty(),
                "停用后 sidecar 工具应被逆序回收");
    }
}
