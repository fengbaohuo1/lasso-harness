package com.jnf.harness.domain.tool.service;

import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.PluginHookResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具统一执行链：查找 → PRE Hook（可拦）→ 审批闸 → 执行（超时/并发受控）→ POST Hook → 结果。
 * <p>失败一律收敛为结构化 {@link ToolExecutionResult#fail}（模型可读），不向上抛异常。
 * <p>事件回填（ToolCall/ToolResult）由 ReAct 循环负责，见 ReactLoopAgent。
 */
public class ToolCallExecutor implements ToolRunner {

    private final ToolRegistry registry;
    private final List<ToolLifecycleHook> hooks;
    private final ApprovalGate approvalGate;
    private final ToolRunContext context;
    private final long timeoutSeconds;
    private final int maxParallel;
    private final ExecutorService workers;
    private final Semaphore semaphore;

    public ToolCallExecutor(ToolRegistry registry, List<ToolLifecycleHook> hooks,
                           ApprovalGate approvalGate, ToolRunContext context,
                           long timeoutSeconds, int maxParallel) {
        this.registry = registry;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
        this.approvalGate = approvalGate == null ? ApprovalGate.ALLOW_ALL : approvalGate;
        this.context = context;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
        this.maxParallel = Math.max(1, maxParallel);
        this.semaphore = new Semaphore(this.maxParallel);
        // R6：有界工具线程池
        this.workers = Executors.newFixedThreadPool(this.maxParallel, r -> {
            Thread thread = new Thread(r, "tool-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ToolCallExecutor(ToolRegistry registry, ToolRunContext context) {
        this(registry, List.of(), ApprovalGate.ALLOW_ALL, context, 300, 10);
    }

    @Override
    public CompletableFuture<ToolExecutionResult> run(ToolCallBlock call) {
        return CompletableFuture.supplyAsync(() -> executeBlocking(call), workers);
    }

    /** 同步执行（测试与调用方需要直接语义时使用）。 */
    public ToolExecutionResult executeBlocking(ToolCallBlock call) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolExecutionResult.fail("工具执行被中断", "TOOL_INTERRUPTED");
        }
        try {
            return doExecute(call);
        } finally {
            semaphore.release();
        }
    }

    private ToolExecutionResult doExecute(ToolCallBlock call) {
        // 1. 查找工具
        var tool = registry.lookup(call.name());
        if (tool.isEmpty()) {
            return ToolExecutionResult.fail("工具不存在: " + call.name(), "TOOL_NOT_FOUND");
        }

        // 2. PRE Hook（BLOCK/DENY 均拦截）
        for (ToolLifecycleHook hook : hooks) {
            PluginHookResult verdict = hook.beforeToolUse(call.name(), call.arguments());
            if (verdict.intercepted()) {
                return ToolExecutionResult.fail(
                        "Blocked by tool hook: " + verdict.reason(), "HOOK_BLOCKED");
            }
        }

        // 3. 审批闸（R1 运行期审批接线点）
        if (!approvalGate.allow(call.name(), call.arguments())) {
            return ToolExecutionResult.fail("工具调用需要审批: " + call.name(), "APPROVAL_REQUIRED");
        }

        // 4. 执行（超时受控）
        ToolExecutionResult result;
        try {
            CompletableFuture<ToolExecutionResult> future =
                    tool.get().execute(call.arguments(), context);
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            result = ToolExecutionResult.fail("工具执行超时（" + timeoutSeconds + "s）", "TOOL_TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = ToolExecutionResult.fail("工具执行被中断", "TOOL_INTERRUPTED");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            result = ToolExecutionResult.fail("工具执行异常: " + cause.getMessage(), "TOOL_ERROR");
        }
        if (result == null) {
            result = ToolExecutionResult.fail("工具返回空结果", "TOOL_ERROR");
        }

        // 5. POST Hook（只观察）
        for (ToolLifecycleHook hook : hooks) {
            try {
                hook.afterToolUse(call.name(), call.arguments(), result);
            } catch (Exception e) {
                // 观察型 Hook 不影响结果
            }
        }
        return result;
    }

    public void shutdown() {
        workers.shutdownNow();
    }
}
