package com.jnf.harness.cases.agent.factory;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.cases.agent.node.AgentCollectNode;
import com.jnf.harness.cases.agent.node.AgentDispatchNode;
import com.jnf.harness.cases.agent.node.AgentIntentNode;
import com.jnf.harness.cases.agent.node.AgentResolveNode;
import com.jnf.harness.cases.orchestration.DynamicContext;
import com.jnf.harness.domain.agent.service.run.AgentEventListener;
import com.jnf.harness.domain.agent.service.run.AgentRunFactory;

/**
 * Agent 消息链工厂：Resolve → Intent → Dispatch → Collect。
 */
public class AgentMessageFactory {

    private final AgentRunFactory agentRunFactory;

    public AgentMessageFactory(AgentRunFactory agentRunFactory) {
        this.agentRunFactory = agentRunFactory;
    }

    /** 建链并执行；返回携带 summary/error 的上下文。 */
    public DynamicContext run(AgentMessageCommand command, AgentEventListener listener, long timeoutSeconds) {
        DynamicContext context = new DynamicContext();
        context.put("command", command);

        AgentCollectNode collect = new AgentCollectNode(timeoutSeconds);
        AgentDispatchNode dispatch = new AgentDispatchNode(collect);
        AgentIntentNode intent = new AgentIntentNode(dispatch);
        AgentResolveNode resolve = new AgentResolveNode(agentRunFactory, listener, intent);

        resolve.execute(context);
        return context;
    }
}
