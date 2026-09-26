package com.jnf.harness.api;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.stream.AgentStreamSink;

/**
 * Agent 门面（流式）。
 */
public interface IAgentStreamApi {

    /** 发起流式对话；事件经 sink 回调（onDone/onError 为终态，之后不再有事件）。 */
    void stream(AgentMessageCommand command, AgentStreamSink sink);
}
