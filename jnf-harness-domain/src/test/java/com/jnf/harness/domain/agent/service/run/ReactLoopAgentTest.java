package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.compaction.BasicCompactionEngine;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.domain.session.event.PersistingSessionLog;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.domain.session.event.TurnEndReason;
import com.jnf.harness.model.ToolExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReAct 循环验收（第 4 节 DoD）：工具续步、步数上限、受控续写、协作取消、压缩触发。
 */
class ReactLoopAgentTest {

    /** 脚本化假 LLM：每次调用消费一段预设增量序列。 */
    static final class FakeLlm implements ILlmRuntimePort {
        final Deque<List<StreamChunk>> scripts = new ArrayDeque<>();
        final List<GenerateOptions> calls = new ArrayList<>();
        final AtomicInteger callCount = new AtomicInteger();
        volatile CountDownLatch blockUntil;

        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            calls.add(options);
            callCount.incrementAndGet();
            List<StreamChunk> script = scripts.isEmpty() ? List.of() : scripts.poll();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                CountDownLatch latch = blockUntil;
                if (latch != null) {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                for (StreamChunk chunk : script) {
                    subscriber.onNext(chunk);
                }
                subscriber.onComplete();
            };
        }
    }

    private ReactLoopAgent newAgent(FakeLlm llm, AgentOptions options,
                                    com.jnf.harness.domain.agent.service.ToolRunner runner) {
        PersistingSessionLog session = new PersistingSessionLog("s", new SessionLog(), null);
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("base", 0, "你是测试智能体。"));
        return new ReactLoopAgent("agent-1", session, llm, options, runner,
                () -> List.of(Map.of("name", "echo", "description", "echo", "parameters", Map.of())),
                assembler,
                new BasicCompactionEngine(events -> "早期历史摘要"), null);
    }

    private AgentOptions options(int maxSteps) {
        return new AgentOptions("m", LlmChannel.openai("http://127.0.0.1:1", "k"),
                maxSteps, 4, 128_000, 6, 100_000L, null, null);
    }

    private static List<StreamChunk> text(String text, FinishReason reason) {
        return List.of(new StreamChunk.TextDelta(text),
                new StreamChunk.DoneChunk(reason, new TokenUsage(3, 5)));
    }

    @Test
    @DisplayName("简单回合：user → assistant → COMPLETED，事件完整、计量正确")
    void simple_turn_completes() throws Exception {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(text("你好", FinishReason.STOP));
        ReactLoopAgent agent = newAgent(llm, options(50), call -> CompletableFuture.completedFuture(
                ToolExecutionResult.ok("unused")));

        Inbox.TurnSummary summary = agent.ask("hi").get(5, TimeUnit.SECONDS);

        assertEquals(TurnEndReason.COMPLETED, summary.reason());
        assertEquals("你好", summary.lastAssistantText());
        assertEquals(8, agent.meter().totalTokens());
        assertEquals(1, llm.callCount.get());

        List<SessionEvent> events = agent.session().log().entries().stream().map(e -> e.event()).toList();
        assertTrue(events.get(0) instanceof SessionEvent.TurnStart);
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.UserMessage));
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.AssistantChunk));
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.AssistantMessage));
        assertTrue(events.get(events.size() - 1) instanceof SessionEvent.TurnEnd);
    }

    @Test
    @DisplayName("工具续步：工具结果必须回到模型上下文，同一 turn 内完成续步")
    void tool_result_continues_step() throws Exception {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(List.of(
                new StreamChunk.ToolCallDelta("c1", "echo", "{\"x\":1}"),
                new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero())));
        llm.scripts.add(text("完成", FinishReason.STOP));

        List<String> toolRuns = new ArrayList<>();
        ReactLoopAgent agent = newAgent(llm, options(50), call -> {
            toolRuns.add(call.name());
            return CompletableFuture.completedFuture(ToolExecutionResult.ok("工具结果"));
        });

        Inbox.TurnSummary summary = agent.ask("执行").get(5, TimeUnit.SECONDS);

        assertEquals(TurnEndReason.COMPLETED, summary.reason());
        assertEquals(List.of("echo"), toolRuns);
        assertEquals(2, llm.callCount.get());
        // 第二次生成的上下文必须包含工具结果
        String secondCallContext = llm.calls.get(1).messages().stream()
                .map(m -> m.text() + m.blocks().toString()).reduce("", (a, b) -> a + b);
        assertTrue(secondCallContext.contains("工具结果") || secondCallContext.contains("ToolResult"),
                "工具结果必须回流到第二次生成的上下文: " + secondCallContext);
        assertTrue(agent.session().log().entries().stream()
                .anyMatch(e -> e.event() instanceof SessionEvent.ToolResult));
    }

    @Test
    @DisplayName("步数上限：强制收口为 MAX_STEPS")
    void max_steps_limit() throws Exception {
        FakeLlm llm = new FakeLlm();
        for (int i = 0; i < 10; i++) {
            llm.scripts.add(List.of(
                    new StreamChunk.ToolCallDelta("c" + i, "echo", "{}"),
                    new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero())));
        }
        ReactLoopAgent agent = newAgent(llm, options(3), call -> CompletableFuture.completedFuture(
                ToolExecutionResult.ok("ok")));

        Inbox.TurnSummary summary = agent.ask("无限工具").get(5, TimeUnit.SECONDS);

        assertEquals(TurnEndReason.MAX_STEPS, summary.reason());
        assertEquals(3, llm.callCount.get());
    }

    @Test
    @DisplayName("受控续写：max_tokens 截断注入续写提示后继续")
    void max_tokens_continuation() throws Exception {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(text("前半", FinishReason.LENGTH));
        llm.scripts.add(text("后半", FinishReason.STOP));
        ReactLoopAgent agent = newAgent(llm, options(50), call -> CompletableFuture.completedFuture(
                ToolExecutionResult.ok("unused")));

        Inbox.TurnSummary summary = agent.ask("写长文").get(5, TimeUnit.SECONDS);

        assertEquals(TurnEndReason.COMPLETED, summary.reason());
        assertEquals(2, llm.callCount.get());
        String secondContext = llm.calls.get(1).messages().stream()
                .map(m -> m.text()).reduce("", (a, b) -> a + b);
        assertTrue(secondContext.contains(ReactLoopAgent.CONTINUATION_PROMPT));
    }

    @Test
    @DisplayName("协作式取消：流中取消立即收口为 ABORTED")
    void cancel_aborts_turn() throws Exception {
        FakeLlm llm = new FakeLlm();
        llm.blockUntil = new CountDownLatch(1);
        llm.scripts.add(text("永远到不了", FinishReason.STOP));
        ReactLoopAgent agent = newAgent(llm, options(50), call -> CompletableFuture.completedFuture(
                ToolExecutionResult.ok("unused")));

        CompletableFuture<Inbox.TurnSummary> future = agent.ask("开始");
        Thread.sleep(150);
        agent.cancel();
        llm.blockUntil.countDown();

        Inbox.TurnSummary summary = future.get(5, TimeUnit.SECONDS);
        assertEquals(TurnEndReason.ABORTED, summary.reason());
    }

    @Test
    @DisplayName("压缩触发：超阈值时早期历史被 Compacted 摘要替换")
    void compaction_triggered() throws Exception {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(text("好", FinishReason.STOP));
        AgentOptions options = new AgentOptions("m", LlmChannel.openai("http://127.0.0.1:1", "k"),
                50, 4, 128_000, 6, 1L, null, null);
        ReactLoopAgent agent = newAgent(llm, options, call -> CompletableFuture.completedFuture(
                ToolExecutionResult.ok("unused")));

        // 预置 8 条消息事件（超过压缩阈值 1 token）
        for (int i = 0; i < 4; i++) {
            agent.session().append(new SessionEvent.UserMessage(0, 0, "历史问题" + i));
            agent.session().append(new SessionEvent.AssistantMessage(0, 0, "历史回答" + i));
        }

        agent.ask("新问题").get(5, TimeUnit.SECONDS);

        assertTrue(agent.session().log().entries().stream()
                        .anyMatch(e -> e.event() instanceof SessionEvent.Compacted),
                "应产生 Compacted 压缩事件");
    }
}
