package com.jnf.harness.app;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.stream.AgentStreamSink;
import com.jnf.harness.cases.agent.AgentUseCaseImpl;
import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.domain.runtime.workspace.WorkspaceService;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.trigger.http.HarnessConsoleQueryController;
import com.jnf.harness.trigger.http.HarnessWorkspaceController;
import com.jnf.harness.trigger.http.PluginApiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P9 验收：工作区管理（CRUD + 会话绑定）+ 沙箱三档权限（运行期切换强制）
 * + 思考链路（ReasoningDelta → 事件/回放/流回调）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.approval.mode=AUTO_APPROVE",
                "harness.events.dir=target/test-events-p9",
                "spring.datasource.url=jdbc:h2:mem:dbp9;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class WorkspaceSandboxThinkingIntegrationTest {

    @Autowired
    WorkspaceService workspaceService;
    @Autowired
    HarnessWorkspaceController workspaceController;
    @Autowired
    HarnessConsoleQueryController consoleController;
    @Autowired
    PluginApiController pluginApiController;
    @Autowired
    ToolRunner toolRunner;
    @Autowired
    AgentUseCaseImpl agentUseCase;

    // ---------------- P9-1 工作区管理 ----------------

    @Test
    @DisplayName("P9-1：工作区 CRUD + 会话绑定解析 + default 保护")
    void workspace_management_and_binding() {
        List<?> list = workspaceController.list().data();
        assertTrue(list.toString().contains("default"), list.toString());

        Map<String, Object> created = workspaceController
                .create(Map.of("displayName", "测试工作区", "rootPath", "target/ws-it-p9")).data();
        String code = String.valueOf(created.get("workspaceCode"));

        // 会话绑定 → cwd 解析到工作区根
        consoleController.updateSessionSettings("it-ws-1", Map.of("workspaceCode", code));
        assertEquals(String.valueOf(created.get("rootPath")),
                workspaceService.cwdFor("it-ws-1"));
        assertTrue(consoleController.sessionSettings("it-ws-1").data()
                .get("workspaceCode").equals(code));

        // default 不可删（信封 40000）
        assertEquals("40000", workspaceController.delete("default").code());

        // 删除后回落 default 根
        workspaceController.delete(code);
        assertEquals(workspaceService.cwdFor("it-ws-none"), workspaceService.cwdFor("it-ws-1"));
    }

    // ---------------- P9-2 沙箱三档 ----------------

    @Test
    @DisplayName("P9-2：三档权限模式运行期切换并强制（只读/工作区内读写/完全权限）")
    void sandbox_three_modes() {
        String session = "it-sbx-1";
        Map<String, Object> created = workspaceController
                .create(Map.of("displayName", "沙箱工作区", "rootPath", "target/ws-sbx-p9")).data();
        String wsRoot = String.valueOf(created.get("rootPath"));
        consoleController.updateSessionSettings(session, Map.of("workspaceCode", created.get("workspaceCode")));

        ToolCallBlock writeInside = ToolCallBlock.of("c1", "fs_write",
                Map.of("path", "inside.txt", "content", "hi"));
        ToolCallBlock writeOutside = ToolCallBlock.of("c2", "fs_write",
                Map.of("path", "../outside.txt", "content", "hi"));
        ToolCallBlock shell = ToolCallBlock.of("c3", "shell_execute", Map.of("command", "sudo rm x"));

        // 只读：写与 shell 全拒
        setSandboxMode(session, "READ_ONLY");
        ToolExecutionResult r1 = runWith(writeInside, session, wsRoot);
        assertTrue(r1.failed() && r1.content().contains("只读"), r1.content());
        ToolExecutionResult r2 = runWith(shell, session, wsRoot);
        assertTrue(r2.failed() && r2.content().contains("只读"), r2.content());

        // 工作区内读写：越界/越逸拒，界内放
        setSandboxMode(session, "WORKSPACE_WRITE");
        ToolExecutionResult r3 = runWith(writeOutside, session, wsRoot);
        assertTrue(r3.failed() && r3.content().contains("越界"), r3.content());
        ToolExecutionResult r4 = runWith(shell, session, wsRoot);
        assertTrue(r4.failed() && r4.content().contains("越界模式"), r4.content());
        ToolExecutionResult r5 = runWith(writeInside, session, wsRoot);
        assertFalse(r5.failed(), r5.content());

        // 完全权限：全放
        setSandboxMode(session, "DANGER_FULL_ACCESS");
        ToolExecutionResult r6 = runWith(writeOutside, session, wsRoot);
        assertFalse(r6.failed(), r6.content());
    }

    private void setSandboxMode(String sessionId, String mode) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PUT", "/api/harness/plugins/sandbox/api/mode");
        var response = pluginApiController.invoke("sandbox", request,
                "{\"sessionId\":\"" + sessionId + "\",\"mode\":\"" + mode + "\"}");
        assertEquals("00000", response.code(), String.valueOf(response.data()));
    }

    private ToolExecutionResult runWith(ToolCallBlock call, String sessionId, String cwd) {
        return toolRunner.run(call, new ToolRunContext() {
            @Override
            public String agentId() {
                return sessionId;
            }

            @Override
            public String sessionId() {
                return sessionId;
            }

            @Override
            public String cwd() {
                return cwd;
            }
        }).join();
    }

    // ---------------- P9-8 思考链路 ----------------

    @Test
    @DisplayName("P9-8：ReasoningDelta → 思考回调 + 事件落盘 + 回放附思考内容")
    void thinking_stream_and_replay() throws Exception {
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.ReasoningDelta("先想想："),
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.ReasoningDelta("答案应是 42"),
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.TextDelta("答案是 42"),
                new com.jnf.harness.domain.llm.model.valobj.StreamChunk.DoneChunk(
                        com.jnf.harness.domain.llm.model.valobj.FinishReason.STOP,
                        new com.jnf.harness.domain.llm.model.valobj.TokenUsage(3, 5))));

        StringBuilder reasoning = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> status = new AtomicReference<>("");
        agentUseCase.stream(new AgentMessageCommand("think-1", "宇宙的终极答案？", null, null, null, null),
                new AgentStreamSink() {
                    @Override
                    public void onMeta(String agentId) {
                    }

                    @Override
                    public void onChunk(String content) {
                    }

                    @Override
                    public void onReasoning(String content) {
                        reasoning.append(content);
                    }

                    @Override
                    public void onStepBreak(String toolName, String callId, String argsJson, String s) {
                    }

                    @Override
                    public void onToolResult(String toolName, String callId, String result, boolean error) {
                    }

                    @Override
                    public void onFinish(long turn) {
                    }

                    @Override
                    public void onDone(String agentId, long turn, String s, String text, int toolCalls) {
                        status.set(s);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String code, String message) {
                        status.set("ERROR:" + code);
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "turn 未在 30s 内收口");
        assertEquals("COMPLETED", status.get());
        assertEquals("先想想：答案应是 42", reasoning.toString());

        // 回放：助手消息附思考内容（P9-8）
        List<?> messages = consoleController.messages("think-1").data();
        String rendered = messages.toString();
        assertTrue(rendered.contains("答案是 42"), rendered);
        assertTrue(rendered.contains("先想想：答案应是 42"), rendered);
    }
}
