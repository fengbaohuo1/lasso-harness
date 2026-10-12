package com.jnf.harness.domain.agent.service;

import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.concurrent.CompletableFuture;

/**
 * 工具执行缝：ReAct 循环只依赖这个最小接口；
 * 第 5 节的 ToolCallExecutor（Hook/审批/超时/回填）实现它。
 */
@FunctionalInterface
public interface ToolRunner {

    CompletableFuture<ToolExecutionResult> run(ToolCallBlock call);

    /** 会话感知形态（P9-1/P9-2）：携带真实 agentId/sessionId/cwd，默认回落无会话形态。 */
    default CompletableFuture<ToolExecutionResult> run(ToolCallBlock call, ToolRunContext context) {
        return run(call);
    }
}
