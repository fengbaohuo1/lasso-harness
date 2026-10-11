package com.jnf.harness.cases.agent.node;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.cases.orchestration.AbstractStrategyRouter;
import com.jnf.harness.cases.orchestration.DynamicContext;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.run.AgentEventListener;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;

import java.util.concurrent.CompletableFuture;

/**
 * 节点三：构造用户消息并投递（agent.ask），future 入上下文。
 */
public class AgentDispatchNode extends AbstractStrategyRouter {

    private final AbstractStrategyRouter next;

    public AgentDispatchNode(AbstractStrategyRouter next) {
        this.next = next;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doApply(DynamicContext context) {
        AgentMessageCommand command = context.get("command", AgentMessageCommand.class);
        ReactLoopAgent agent = context.get("agent", ReactLoopAgent.class);
        AgentEventListener listener = context.get("listener", AgentEventListener.class);
        // F2/A1：per-message model + profile 透传（空=生效渠道/default 画像）
        CompletableFuture<Inbox.TurnSummary> future = listener == null
                ? agent.ask(command.message(), command.model(), command.profile(), null)
                : agent.ask(command.message(), command.model(), command.profile(), listener);
        context.put("future", future);
    }

    @Override
    protected AbstractStrategyRouter getNext() {
        return next;
    }
}
