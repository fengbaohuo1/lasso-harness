package com.jnf.harness.api;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.dto.AgentMessageResult;
import com.jnf.harness.api.response.Response;

/**
 * Agent 门面（阻塞式）。
 */
public interface IAgentApi {

    Response<AgentMessageResult> send(AgentMessageCommand command);

    Response<AgentMessageResult> status(String agentId);

    Response<Void> cancel(String agentId);
}
