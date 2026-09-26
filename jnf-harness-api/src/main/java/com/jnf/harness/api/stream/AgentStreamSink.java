package com.jnf.harness.api.stream;

/**
 * 流式输出缝：case 层把 Agent 事件转成这些回调，trigger 层映射为 SSE 帧。
 * <p>事件名对齐协议：meta / chunk / step_break / tool_result / finish / done / error。
 */
public interface AgentStreamSink {

    void onMeta(String agentId);

    void onChunk(String content);

    void onStepBreak(String toolName, String callId, String argsJson, String status);

    void onToolResult(String toolName, String callId, String result, boolean error);

    void onFinish(long turn);

    void onDone(String agentId, long turn, String status, String text, int toolCalls);

    void onError(String code, String message);
}
