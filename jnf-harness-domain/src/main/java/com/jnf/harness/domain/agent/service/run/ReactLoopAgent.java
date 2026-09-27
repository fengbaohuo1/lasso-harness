package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.model.Phase;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.domain.agent.service.compaction.CompactionEngine;
import com.jnf.harness.domain.llm.LlmException;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.domain.llm.service.BlockAssembler;
import com.jnf.harness.domain.runtime.meter.SessionTokenMeter;
import com.jnf.harness.domain.session.event.PersistingSessionLog;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.TurnEndReason;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * ReAct 循环核心：kick（驱动线程）→ turn（消息回合）→ step（一次 LLM 流式生成 + 可选工具调用）。
 * <p>关键机制（对齐参照实现）：
 * <ul>
 *   <li><b>工具续步</b>：工具执行完继续 step，不因 inbox 为空退出（模型必须看到工具结果）；</li>
 *   <li><b>步数上限</b>：单 turn ≤ maxStepsPerTurn（默认 50）；</li>
 *   <li><b>受控续写</b>：输出被 max_tokens 截断时注入续写提示，最多 maxTokenContinuations 次；</li>
 *   <li><b>协作式取消</b>：cancel() 置取消信号并取消流订阅，step 边界生效；</li>
 *   <li><b>上下文预算</b>：deriveMessages 后按预算裁剪（保留最近 keepRecentMessages 条）；</li>
 *   <li><b>压缩</b>：turn 开始前按估算 token 触发 CompactionEngine。</li>
 * </ul>
 */
public class ReactLoopAgent {

    public static final String CONTINUATION_PROMPT = "请继续完成未完成的输出。";

    private final String agentId;
    private final PersistingSessionLog session;
    private final ILlmRuntimePort llm;
    private final Supplier<AgentOptions> optionsRef;
    private final ToolRunner toolRunner;
    private final Supplier<List<Map<String, Object>>> toolSchemas;
    private final SystemPromptAssembler promptAssembler;
    private final CompactionEngine compactionEngine;
    private final AgentEventListener listener;
    private final SessionTokenMeter meter = new SessionTokenMeter();
    private final Inbox inbox = new Inbox();
    private final ExecutorService driver;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean abort = new AtomicBoolean(false);
    private final AtomicLong turnCounter = new AtomicLong();
    private final AtomicReference<Flow.Subscription> currentSubscription = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> currentGeneration = new AtomicReference<>();
    private volatile Phase phase = new Phase.Idle();

    public ReactLoopAgent(String agentId, PersistingSessionLog session, ILlmRuntimePort llm,
                          Supplier<AgentOptions> optionsRef, ToolRunner toolRunner,
                          Supplier<List<Map<String, Object>>> toolSchemas,
                          SystemPromptAssembler promptAssembler,
                          CompactionEngine compactionEngine, AgentEventListener listener) {
        this.agentId = agentId;
        this.session = session;
        this.llm = llm;
        this.optionsRef = optionsRef;
        this.toolRunner = toolRunner;
        this.toolSchemas = toolSchemas;
        this.promptAssembler = promptAssembler;
        this.compactionEngine = compactionEngine;
        this.listener = listener == null ? new AgentEventListener() {
        } : listener;
        // R6：有界单线程驱动（Agent 循环本身串行，工具并发由 ToolRunner 内部有界池负责）
        this.driver = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "agent-driver-" + agentId);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 提交一条用户消息；future 在对应 turn 结束时完成。 */
    public CompletableFuture<Inbox.TurnSummary> ask(String text) {
        return ask(text, null);
    }

    /** 提交一条用户消息并挂本问专属流式监听。 */
    public CompletableFuture<Inbox.TurnSummary> ask(String text, AgentEventListener listener) {
        Inbox.PendingMessage message = new Inbox.PendingMessage(text, new CompletableFuture<>(), listener);
        inbox.add(message);
        if (started.compareAndSet(false, true)) {
            driver.submit(this::driverLoop);
        }
        return message.future();
    }

    /** 协作式取消当前 turn。 */
    public void cancel() {
        abort.set(true);
        Flow.Subscription subscription = currentSubscription.get();
        if (subscription != null) {
            subscription.cancel();
        }
        CompletableFuture<Void> generation = currentGeneration.get();
        if (generation != null) {
            // 立即解除 generate() 的等待（部分结果按取消语义丢弃）
            generation.complete(null);
        }
    }

    public void shutdown() {
        closed.set(true);
        cancel();
        driver.shutdownNow();
    }

    public SessionTokenMeter meter() {
        return meter;
    }

    public PersistingSessionLog session() {
        return session;
    }

    public String agentId() {
        return agentId;
    }

    public Phase phase() {
        return phase;
    }

    // ---------------- 驱动与回合 ----------------

    private void driverLoop() {
        while (!closed.get()) {
            try {
                List<Inbox.PendingMessage> claimed = inbox.claimAll(500);
                if (!claimed.isEmpty()) {
                    runTurn(claimed);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 驱动线程兜底：单 turn 失败不杀死循环
            }
        }
    }

    private void runTurn(List<Inbox.PendingMessage> claimed) {
        long turn = turnCounter.incrementAndGet();
        AtomicBoolean turnAbort = new AtomicBoolean(false);
        phase = new Phase.Running(turnAbort, turn, 0);
        abort.set(false);
        // 事件分发：默认监听 + 本 turn 首个消息的专属监听
        AgentEventListener perTurn = claimed.stream()
                .map(Inbox.PendingMessage::listener)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        AgentEventListener events = mergeListeners(this.listener, perTurn);
        session.append(new SessionEvent.TurnStart(turn));
        runCompactionIfHeavy();

        long step = 0;
        for (Inbox.PendingMessage message : claimed) {
            session.append(new SessionEvent.UserMessage(turn, 1, message.text()));
        }

        int tokenContinuations = 0;
        StringBuilder lastAssistant = new StringBuilder();
        TurnEndReason endReason = null;
        String detail = "";

        try {
            while (true) {
                if (abort.get() || turnAbort.get()) {
                    endReason = TurnEndReason.ABORTED;
                    break;
                }
                if (step >= options().maxStepsPerTurn()) {
                    endReason = TurnEndReason.MAX_STEPS;
                    detail = "max steps per turn exceeded";
                    break;
                }

                step++;
                phase = new Phase.Running(turnAbort, turn, step);
                session.append(new SessionEvent.StepStart(turn, step));

                BlockAssembler.GenerationResult result = generate(turn, step, events);

                session.append(new SessionEvent.StepEnd(turn, step));
                if (abort.get() || turnAbort.get()) {
                    // 取消后部分结果按取消语义丢弃
                    endReason = TurnEndReason.ABORTED;
                    break;
                }
                meter.record(result.usage());

                if (result.finishReason() == FinishReason.LENGTH
                        && tokenContinuations < options().maxTokenContinuations()) {
                    // max_tokens 截断：受控续写
                    tokenContinuations++;
                    if (!result.text().isBlank()) {
                        appendAssistant(turn, step, result.text(), lastAssistant);
                    }
                    session.append(new SessionEvent.UserMessage(turn, step, CONTINUATION_PROMPT));
                    continue;
                }

                if (!result.text().isBlank()) {
                    appendAssistant(turn, step, result.text(), lastAssistant);
                }

                if (result.toolCalls().isEmpty()) {
                    endReason = TurnEndReason.COMPLETED;
                    break;
                }

                for (ToolCallBlock call : result.toolCalls()) {
                    session.append(new SessionEvent.ToolCall(turn, step, call.callId(), call.name(), call.arguments()));
                    events.onToolCall(call);
                    ToolExecutionResult toolResult = executeTool(call);
                    session.append(new SessionEvent.ToolResult(turn, step, call.callId(), call.name(),
                            toolResult.failed(), toolResult.content()));
                    events.onToolResult(call, toolResult);
                    if (abort.get()) {
                        break;
                    }
                }
                // 工具续步语义：工具执行完直接进入下一步（模型必须看到工具结果），
                // 不做"输入箱为空就结束"的检查——这正是 ReAct 循环最常见的丢工具结果 bug 的解法
            }
        } catch (LlmException e) {
            endReason = TurnEndReason.ERROR;
            detail = e.failure().code() + ": " + e.failure().message();
        } catch (Exception e) {
            endReason = TurnEndReason.ERROR;
            detail = String.valueOf(e.getMessage());
        }

        TurnEndReason finalReason = endReason == null ? TurnEndReason.ERROR : endReason;
        session.append(new SessionEvent.TurnEnd(turn, finalReason, detail));
        Inbox.TurnSummary summary = new Inbox.TurnSummary(agentId, turn, lastAssistant.toString(), finalReason);
        for (Inbox.PendingMessage message : claimed) {
            if (finalReason == TurnEndReason.ERROR) {
                message.future().completeExceptionally(new IllegalStateException(detail));
            } else {
                message.future().complete(summary);
            }
        }
        listener.onTurnEnd(summary);
        if (perTurn != null) {
            perTurn.onTurnEnd(summary);
        }
        phase = new Phase.Idle();
    }

    /** 合并两个监听器（任一为空只发另一个）。 */
    private static AgentEventListener mergeListeners(AgentEventListener first, AgentEventListener second) {
        if (second == null) {
            return first;
        }
        if (first == null) {
            return second;
        }
        AgentEventListener a = first;
        AgentEventListener b = second;
        return new AgentEventListener() {
            @Override
            public void onChunk(String text) {
                a.onChunk(text);
                b.onChunk(text);
            }

            @Override
            public void onToolCall(ToolCallBlock call) {
                a.onToolCall(call);
                b.onToolCall(call);
            }

            @Override
            public void onToolResult(ToolCallBlock call, ToolExecutionResult result) {
                a.onToolResult(call, result);
                b.onToolResult(call, result);
            }

            @Override
            public void onTurnEnd(Inbox.TurnSummary summary) {
                a.onTurnEnd(summary);
                b.onTurnEnd(summary);
            }
        };
    }

    private void appendAssistant(long turn, long step, String text, StringBuilder lastAssistant) {
        session.append(new SessionEvent.AssistantMessage(turn, step, text));
        lastAssistant.setLength(0);
        lastAssistant.append(text);
    }

    private ToolExecutionResult executeTool(ToolCallBlock call) {
        try {
            CompletableFuture<ToolExecutionResult> future = toolRunner.run(call);
            return future.get(300, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return ToolExecutionResult.fail("工具执行超时", "TOOL_TIMEOUT");
        } catch (Exception e) {
            return ToolExecutionResult.fail("工具执行失败: " + e.getMessage(), "TOOL_ERROR");
        }
    }

    // ---------------- 生成 ----------------

    private BlockAssembler.GenerationResult generate(long turn, long step, AgentEventListener events) {
        List<Message> messages = buildMessages();
        // M4：每步按最新生效渠道解析（渠道热切换对下一次生成即生效）
        AgentOptions options = options();
        GenerateOptions generateOptions = GenerateOptions.builder()
                .channel(options.channel())
                .model(options.model())
                .messages(messages)
                .toolSchemas(toolSchemas.get())
                .maxTokens(options.maxTokens())
                .temperature(options.temperature())
                .build();

        BlockAssembler assembler = new BlockAssembler();
        CompletableFuture<Void> done = new CompletableFuture<>();
        currentGeneration.set(done);
        llm.stream(generateOptions).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                currentSubscription.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk chunk) {
                assembler.accept(chunk);
                if (chunk instanceof StreamChunk.TextDelta delta) {
                    session.append(new SessionEvent.AssistantChunk(turn, step, delta.text()));
                    events.onChunk(delta.text());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        try {
            done.get(130, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new LlmException(com.jnf.harness.domain.llm.model.valobj.LlmFailure.timeout("LLM 生成超时"));
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof LlmException le) {
                throw le;
            }
            throw new LlmException(com.jnf.harness.domain.llm.model.valobj.LlmFailure.upstream(
                    String.valueOf(cause.getMessage()), true));
        } finally {
            currentSubscription.set(null);
            currentGeneration.set(null);
        }
        return assembler.result();
    }

    /** 上下文预算裁剪：超出预算时保留最近 keepRecentMessages 条。 */
    private List<Message> buildMessages() {
        List<Message> derived = session.log().deriveMessages();
        String systemPrompt = promptAssembler.assemble();
        List<Message> messages = new ArrayList<>(derived.size() + 1);
        if (!systemPrompt.isBlank()) {
            messages.add(Message.ofText(MessageSource.SYSTEM, systemPrompt));
        }
        messages.addAll(derived);

        long budget = options().contextTokenBudget();
        while (estimateTokens(messages) > budget
                && messages.size() > options().keepRecentMessages() + 1) {
            // 优先裁掉最旧的非系统消息
            int removeIndex = messages.get(0).source() == MessageSource.SYSTEM ? 1 : 0;
            messages.remove(removeIndex);
        }
        return messages;
    }

    private void runCompactionIfHeavy() {
        long estimate = estimateTokens(session.log().deriveMessages());
        compactionEngine.compactIfNeeded(session.log(), estimate, options().compactionThresholdTokens());
    }

    /** 运行选项（M4：turn/step 级解析，支撑渠道热切换）。 */
    private AgentOptions options() {
        return optionsRef.get();
    }

    private long estimateTokens(List<Message> messages) {
        long chars = 0;
        for (Message message : messages) {
            chars += message.text().length();
        }
        return chars / 4;
    }
}
