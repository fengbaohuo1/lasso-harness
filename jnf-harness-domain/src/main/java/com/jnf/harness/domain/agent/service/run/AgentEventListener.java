package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;

/**
 * Agent 流式观测缝：SSE/前端层按 ask 挂接（每问一监听）。
 */
public interface AgentEventListener {

    default void onChunk(String text) {
    }

    /** 思考内容流增量（P9-8）。 */
    default void onReasoning(String text) {
    }

    default void onToolCall(ToolCallBlock call) {
    }

    default void onToolResult(ToolCallBlock call, ToolExecutionResult result) {
    }

    default void onTurnEnd(Inbox.TurnSummary summary) {
    }
}
