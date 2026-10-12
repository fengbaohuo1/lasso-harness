package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.compaction.BasicCompactionEngine;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.spi.host.AgentRunService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8-3 子智能体宿主验收：派发跑一轮（独立子会话）、递归深度裁决（≤2）、超时收口、空 task 拒绝。
 */
class SubagentHostServiceTest {

    /** 脚本化假 LLM：每次调用消费一段预设增量序列；blockUntil 用于超时场景。 */
    static final class FakeLlm implements ILlmRuntimePort {
        final Deque<List<StreamChunk>> scripts = new ArrayDeque<>();
        volatile CountDownLatch blockUntil;

        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
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

    private static AgentRunFactory factory(FakeLlm llm) {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("base", 0, "你是测试智能体。"));
        return new AgentRunFactory(llm,
                call -> CompletableFuture.completedFuture(ToolExecutionResult.ok("unused")),
                (hint, profile, thinking) -> new AgentOptions("m", LlmChannel.openai("http://127.0.0.1:1", "k"),
                        50, 4, 128_000, 6, 100_000L, null, null),
                profile -> List.of(Map.of("name", "echo", "description", "echo", "parameters", Map.of())),
                null, assembler, new BasicCompactionEngine(events -> "早期历史摘要"));
    }

    private static List<StreamChunk> text(String value) {
        return List.of(new StreamChunk.TextDelta(value),
                new StreamChunk.DoneChunk(FinishReason.STOP, new TokenUsage(3, 5)));
    }

    @Test
    @DisplayName("派发：独立子会话跑一轮返回汇报，深度表记 1")
    void dispatch_returns_report() {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(text("子任务完成：报告如下"));
        SubagentHostService service = new SubagentHostService(factory(llm));

        AgentRunService.SubagentOutcome outcome = service.run(
                new AgentRunService.SubagentRequest("top", "调研 X", null, null, 30));

        assertTrue(outcome.ok(), outcome.error());
        assertTrue(outcome.sessionId().startsWith("sub-"));
        assertEquals("子任务完成：报告如下", outcome.text());
        assertEquals(1, service.depthOf(outcome.sessionId()));
    }

    @Test
    @DisplayName("递归防护：子→孙可派发（深度 2），曾孙（深度 3）被拒")
    void recursion_guard_rejects_deep_nesting() {
        FakeLlm llm = new FakeLlm();
        llm.scripts.add(text("一层完成"));
        llm.scripts.add(text("二层完成"));
        SubagentHostService service = new SubagentHostService(factory(llm));

        AgentRunService.SubagentOutcome child = service.run(
                new AgentRunService.SubagentRequest("top", "任务A", null, null, 30));
        AgentRunService.SubagentOutcome grand = service.run(
                new AgentRunService.SubagentRequest(child.sessionId(), "任务B", null, null, 30));
        assertTrue(child.ok() && grand.ok());
        assertEquals(2, service.depthOf(grand.sessionId()));

        AgentRunService.SubagentOutcome great = service.run(
                new AgentRunService.SubagentRequest(grand.sessionId(), "任务C", null, null, 30));
        assertFalse(great.ok(), "深度 3 应被拒绝");
        assertTrue(great.error().contains("嵌套过深"), great.error());
    }

    @Test
    @DisplayName("防御：空 task 拒绝；LLM 挂起时超时收口不挂死")
    void guards_and_timeout() {
        SubagentHostService service = new SubagentHostService(factory(new FakeLlm()));
        assertFalse(service.run(new AgentRunService.SubagentRequest("top", " ", null, null, 5)).ok());

        FakeLlm llm = new FakeLlm();
        llm.blockUntil = new CountDownLatch(1);
        SubagentHostService blocking = new SubagentHostService(factory(llm));
        long started = System.nanoTime();
        AgentRunService.SubagentOutcome timedOut = blocking.run(
                new AgentRunService.SubagentRequest("top", "卡住的任务", null, null, 1));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertFalse(timedOut.ok());
        assertTrue(timedOut.error().contains("超时"), timedOut.error());
        assertTrue(elapsedMillis < 5_000, "超时收口应在秒级返回，实际 " + elapsedMillis + "ms");
    }
}
