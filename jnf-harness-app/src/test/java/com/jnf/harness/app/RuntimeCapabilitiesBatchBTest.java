package com.jnf.harness.app;

import com.jnf.harness.domain.extension.ExtensionManagementService;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.terminal.TerminalService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.domain.workflow.WorkflowService;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 12b 批验收：workflow / terminal / extension / session_search。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:rtbtest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "harness.skills.state-file=target/test-skills.properties",
                "harness.skills.user-dir=target/test-skills",
                "harness.skills.roots="})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class RuntimeCapabilitiesBatchBTest {

    @Autowired
    WorkflowService workflowService;

    @Autowired
    TerminalService terminalService;

    @Autowired
    ExtensionManagementService extensionService;

    @Autowired
    ISessionEventStore eventStore;

    @Autowired
    ToolRegistry toolRegistry;

    private final ToolRunContext ctx = new ToolRunContext() {
        @Override
        public String agentId() {
            return "a";
        }

        @Override
        public String sessionId() {
            return "rtb-session";
        }

        @Override
        public String cwd() {
            return ".";
        }
    };

    @Test
    @DisplayName("terminal：打开→发送→读取输出往返")
    void terminal_round_trip() throws Exception {
        String sessionId = terminalService.open();
        terminalService.send(sessionId, "echo term-ok");
        String output = "";
        for (int i = 0; i < 50; i++) {
            output = terminalService.read(sessionId).output();
            if (output.contains("term-ok")) {
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(output.contains("term-ok"), output);
        terminalService.close(sessionId);
    }

    @Test
    @DisplayName("workflow：顺序步骤执行到 COMPLETED")
    void workflow_runs_to_completion() throws Exception {
        WorkflowService.WorkflowRun run = workflowService.start(List.of("步骤一", "步骤二"));
        WorkflowService.WorkflowRun finished = null;
        for (int i = 0; i < 150; i++) {
            finished = workflowService.get(run.runId());
            if ("COMPLETED".equals(finished.status()) || "FAILED".equals(finished.status())) {
                break;
            }
            Thread.sleep(20);
        }
        assertEquals("COMPLETED", finished.status());
        assertEquals(2, finished.currentStep());
    }

    @Test
    @DisplayName("extension：MCP/CLI 配置 + skill 工具接线；session_search 跨会话命中")
    void extension_and_session_search() throws Exception {
        extensionService.addMcpServer("demo-mcp", List.of("echo", "hi"));
        assertEquals(1, extensionService.listMcpServers().size());
        extensionService.removeMcpServer("demo-mcp");
        extensionService.setCli("editor", "idea");
        assertEquals("idea", extensionService.cliConfig().get("editor"));

        // 技能运行时接线：extension_skills 与 skill 工具都应注册
        assertTrue(toolRegistry.lookup("extension_skills").isPresent());
        assertTrue(toolRegistry.lookup("skill").isPresent());

        eventStore.append("searchable-session", new SessionEventEntry(1,
                new SessionEvent.UserMessage(1, 1, "独特的搜索关键词xyz")));
        ToolExecutionResult result = toolRegistry.lookup("session_search").orElseThrow()
                .execute(Map.of("query", "关键词xyz"), ctx).get();
        assertTrue(result.success());
        assertTrue(result.content().contains("searchable-session"), result.content());

        // lsp 存根：结构化失败而非异常
        ToolExecutionResult lsp = toolRegistry.lookup("lsp_query").orElseThrow()
                .execute(Map.of("kind", "definition", "file", "A.java", "line", "1"), ctx).get();
        assertEquals("LSP_NOT_CONFIGURED", lsp.errorCode());
    }
}
