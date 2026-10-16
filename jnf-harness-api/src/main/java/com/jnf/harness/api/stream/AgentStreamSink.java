package com.jnf.harness.api.stream;

import com.jnf.harness.model.UsageReport;

/**
 * 流式输出缝：case 层把 Agent 事件转成这些回调，trigger 层映射为 SSE 帧。
 * <p>事件名对齐协议：meta / thinking / chunk / step_break / tool_result / usage / finish / done / error。
 */
public interface AgentStreamSink {

    void onMeta(String agentId);

    void onChunk(String content);

    /** P9-8：思考内容流增量。 */
    default void onReasoning(String content) {
    }

    void onStepBreak(String toolName, String callId, String argsJson, String status);

    void onToolResult(String toolName, String callId, String result, boolean error);

    /** P11-5：用量快照（累计 prompt/completion/cached + 最近一次调用的耗时与速度）。 */
    default void onUsage(UsageReport report) {
    }

    void onFinish(long turn);

    void onDone(String agentId, long turn, String status, String text, int toolCalls);

    void onError(String code, String message);
}
