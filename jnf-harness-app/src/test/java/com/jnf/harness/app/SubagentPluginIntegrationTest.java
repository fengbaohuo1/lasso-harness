package com.jnf.harness.app;

import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.trigger.http.HarnessPluginController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8-2/P8-3 验收：子智能体插件 E2E（classpath 激活 → delegate_task 派发 → 子会话汇报回填）
 * + 插件贡献面聚合（tools/endpoints/slashCommands 一屏看全）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.events.dir=target/test-events-subagent",
                "spring.datasource.url=jdbc:h2:mem:dbsubagent;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class SubagentPluginIntegrationTest {

    @Autowired
    ToolRegistry toolRegistry;

    @Autowired
    HarnessPluginController pluginController;

    @Test
    @DisplayName("P8-3 E2E：delegate_task 派发子任务 → 独立子会话跑一轮 → 汇报回填（JSON 结构）")
    void delegate_task_end_to_end() throws Exception {
        ToolDefinition tool = toolRegistry.lookup("delegate_task").orElseThrow();
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.TextDelta("子任务完成：报告如下"),
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.DoneChunk(
                        com.jnf.harness.domain.llm.model.valobj.FinishReason.STOP,
                        new com.jnf.harness.domain.llm.model.valobj.TokenUsage(3, 5))));

        ToolExecutionResult result = tool.execute(
                Map.of("task", "调研 X"), new StubToolContext("parent-1")).get(60, TimeUnit.SECONDS);

        assertTrue(result.success(), result.content());
        assertTrue(result.content().contains("\"sessionId\":\"sub-"), result.content());
        assertTrue(result.content().contains("子任务完成：报告如下"), result.content());
        assertTrue(result.content().contains("\"turn\":1"), result.content());
    }

    @Test
    @DisplayName("P8-2 贡献面：subagent 插件 tools/slashCommands 聚合可见")
    void subagent_contributions_visible() {
        Map<String, Object> data = pluginController.contributions("subagent").data();
        assertEquals("ACTIVE", data.get("runtimeState"));
        List<?> tools = (List<?>) data.get("tools");
        assertEquals(1, tools.size());
        assertTrue(String.valueOf(tools.get(0)).contains("delegate_task"), String.valueOf(tools.get(0)));
        assertNotNull(data.get("slashCommands"));
        assertTrue(String.valueOf(data.get("slashCommands")).contains("delegate"));
    }

    /** 工具执行上下文桩（P8-3：parentSessionId 由 ctx.sessionId() 传入宿主做深度治理）。 */
    private record StubToolContext(String sessionId) implements ToolRunContext {
        @Override
        public String agentId() {
            return sessionId;
        }

        @Override
        public String cwd() {
            return ".";
        }
    }
}
