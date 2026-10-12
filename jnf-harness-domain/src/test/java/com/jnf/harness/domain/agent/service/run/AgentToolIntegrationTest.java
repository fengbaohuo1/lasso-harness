package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.compaction.BasicCompactionEngine;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.session.event.PersistingSessionLog;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.domain.session.event.TurnEndReason;
import com.jnf.harness.plugin.fs.FsWriteTool;
import com.jnf.harness.domain.tool.service.DefaultToolRunContext;
import com.jnf.harness.domain.tool.service.InMemoryToolRegistry;
import com.jnf.harness.domain.tool.service.ToolCallExecutor;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纵向打通（第 5 节 DoD）：LLM 发起工具调用 → ToolCallExecutor → 真实执行 → 结果回填 → 续步收口。
 */
class AgentToolIntegrationTest {

    @Test
    @DisplayName("LLM 调 fs_write 真实落盘，工具结果回流后续步完成")
    void llm_toolcall_writes_file_end_to_end(@TempDir Path tempDir) throws Exception {
        ReactLoopAgentTest.FakeLlm llm = new ReactLoopAgentTest.FakeLlm();
        llm.scripts.add(List.of(
                new StreamChunk.ToolCallDelta("c1", "fs_write", "{\"path\":\"hello.txt\",\"content\":\"来自智能体\"}"),
                new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero())));
        llm.scripts.add(List.of(
                new StreamChunk.TextDelta("已完成写文件"),
                new StreamChunk.DoneChunk(FinishReason.STOP, new TokenUsage(1, 1))));

        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new FsWriteTool());
        ToolCallExecutor executor = new ToolCallExecutor(registry,
                new DefaultToolRunContext("agent-1", "s-1", tempDir.toString()));

        PersistingSessionLog session = new PersistingSessionLog("s-1", new SessionLog(), null);
        ReactLoopAgent agent = new ReactLoopAgent("agent-1", session, llm,
                (hint, profile, thinking) -> AgentOptions.of("m", LlmChannel.openai("http://127.0.0.1:1", "k")),
                executor, profile -> List.of(Map.of("name", "fs_write", "description", "写文件",
                        "parameters", Map.of("type", "object"))),
                new SystemPromptAssembler(), new BasicCompactionEngine(events -> "摘要"), null,
                // P9-1：会话工作区根解析（工具 cwd 按会话注入）
                sessionId -> tempDir.toString());

        Inbox.TurnSummary summary = agent.ask("写个文件").get(10, TimeUnit.SECONDS);

        assertEquals(TurnEndReason.COMPLETED, summary.reason());
        assertTrue(Files.exists(tempDir.resolve("hello.txt")));
        assertEquals("来自智能体", Files.readString(tempDir.resolve("hello.txt")));
        assertTrue(session.log().entries().stream()
                .anyMatch(e -> e.event() instanceof SessionEvent.ToolResult tr
                        && tr.content().contains("已写入")));
        assertEquals("已完成写文件", summary.lastAssistantText());
    }
}
