package com.jnf.harness.cases.agent.node;

import com.jnf.harness.cases.orchestration.AbstractStrategyRouter;
import com.jnf.harness.cases.orchestration.DynamicContext;
import com.jnf.harness.domain.agent.service.Inbox;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 节点四：等待 turn 结束并收集结果（summary / error 入上下文）。
 */
public class AgentCollectNode extends AbstractStrategyRouter {

    private final long timeoutSeconds;

    public AgentCollectNode(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    protected void doApply(DynamicContext context) {
        CompletableFuture<Inbox.TurnSummary> future = context.get("future", CompletableFuture.class);
        try {
            Inbox.TurnSummary summary = future.get(timeoutSeconds, TimeUnit.SECONDS);
            context.put("summary", summary);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            context.put("error", String.valueOf(cause.getMessage()));
        }
    }

    @Override
    protected AbstractStrategyRouter getNext() {
        return null;
    }
}
