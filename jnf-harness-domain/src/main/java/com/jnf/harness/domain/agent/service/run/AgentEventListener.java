package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.UsageReport;

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

    /**
     * 每次 LLM 调用结束后的用量快照（P11-5）：会话累计用量 + 最近一次调用的耗时/速度。
     * 前端据此实时显示 tok/s 与缓存命中率，无需轮询。
     */
    default void onUsage(UsageReport report) {
    }
}
