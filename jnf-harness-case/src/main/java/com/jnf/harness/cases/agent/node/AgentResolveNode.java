package com.jnf.harness.cases.agent.node;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.cases.orchestration.AbstractStrategyRouter;
import com.jnf.harness.cases.orchestration.DynamicContext;
import com.jnf.harness.domain.agent.service.run.AgentEventListener;
import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;

/**
 * 节点一：按 agentId 复用/创建 AgentRun。
 */
public class AgentResolveNode extends AbstractStrategyRouter {

    private final AgentRunFactory factory;
    private final AgentEventListener listener;
    private final AbstractStrategyRouter next;

    public AgentResolveNode(AgentRunFactory factory, AgentEventListener listener,
                            AbstractStrategyRouter next) {
        this.factory = factory;
        this.listener = listener;
        this.next = next;
    }

    @Override
    protected void doApply(DynamicContext context) {
        AgentMessageCommand command = context.get("command", AgentMessageCommand.class);
        ReactLoopAgent agent = factory.getOrCreate(command.agentId(), null);
        context.put("agent", agent);
        if (listener != null) {
            context.put("listener", listener);
        }
    }

    @Override
    protected AbstractStrategyRouter getNext() {
        return next;
    }
}
