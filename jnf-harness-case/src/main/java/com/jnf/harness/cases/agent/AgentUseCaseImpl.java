package com.jnf.harness.cases.agent;

import com.jnf.harness.api.IAgentApi;
import com.jnf.harness.api.IAgentStreamApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.dto.AgentMessageResult;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.api.stream.AgentStreamSink;
import com.jnf.harness.cases.agent.factory.AgentMessageFactory;
import com.jnf.harness.cases.orchestration.DynamicContext;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.run.AgentEventListener;
import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;
import com.jnf.harness.domain.session.event.TurnEndReason;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;

/**
 * Agent 用例：第 7 节起对话链走策略树（Resolve→Intent→Dispatch→Collect），对外签名不变。
 */
public class AgentUseCaseImpl implements IAgentApi, IAgentStreamApi {

    private static final long SEND_TIMEOUT_SECONDS = 300;

    private final AgentMessageFactory messageFactory;
    private final AgentRunFactory agentRunFactory;

    public AgentUseCaseImpl(AgentRunFactory agentRunFactory) {
        this.messageFactory = new AgentMessageFactory(agentRunFactory);
        this.agentRunFactory = agentRunFactory;
    }

    @Override
    public Response<AgentMessageResult> send(AgentMessageCommand command) {
        try {
            DynamicContext context = messageFactory.run(command, null, SEND_TIMEOUT_SECONDS);
            String error = context.get("error", String.class);
            if (error != null) {
                return Response.internalError("对话失败: " + error);
            }
            return Response.success(toResult(context.get("summary", Inbox.TurnSummary.class)));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        } catch (Exception e) {
            return Response.internalError("对话失败: " + rootMessage(e));
        }
    }

    @Override
    public Response<AgentMessageResult> status(String agentId) {
        return agentRunFactory.get(agentId)
                .map(agent -> Response.success(new AgentMessageResult(
                        agentId, 0, agent.phase().getClass().getSimpleName().toUpperCase(),
                        "", (int) agent.meter().llmCalls())))
                .orElseGet(() -> Response.success(
                        new AgentMessageResult(agentId, 0, "UNKNOWN", "", 0)));
    }

    @Override
    public Response<Void> cancel(String agentId) {
        agentRunFactory.get(agentId).ifPresent(ReactLoopAgent::cancel);
        return Response.success(null);
    }

    @Override
    public void stream(AgentMessageCommand command, AgentStreamSink sink) {
        sink.onMeta(command.agentId());
        AgentEventListener listener = new AgentEventListener() {
            private int toolCalls;

            @Override
            public void onChunk(String text) {
                sink.onChunk(text);
            }

            @Override
            public void onToolCall(ToolCallBlock call) {
                toolCalls++;
                sink.onStepBreak(call.name(), call.callId(), toJson(call.arguments()), "begin");
            }

            @Override
            public void onToolResult(ToolCallBlock call, ToolExecutionResult result) {
                sink.onToolResult(call.name(), call.callId(), result.content(), result.failed());
            }

            @Override
            public void onTurnEnd(Inbox.TurnSummary summary) {
                if (summary.reason() == TurnEndReason.ERROR) {
                    // 错误终态走 onError，不发 done（终态唯一）
                    return;
                }
                sink.onFinish(summary.turn());
                sink.onDone(summary.agentId(), summary.turn(), summary.reason().name(),
                        summary.lastAssistantText(), toolCalls);
            }
        };
        // 流式路径：策略树异步执行（Collect 阻塞不占请求线程）
        Thread worker = new Thread(() -> {
            try {
                DynamicContext context = messageFactory.run(command, listener, SEND_TIMEOUT_SECONDS);
                String error = context.get("error", String.class);
                if (error != null) {
                    sink.onError("TURN_ERROR", error);
                }
            } catch (Exception e) {
                sink.onError("STREAM_ERROR", rootMessage(e));
            }
        }, "agent-stream-" + command.agentId());
        worker.setDaemon(true);
        worker.start();
    }

    private AgentMessageResult toResult(Inbox.TurnSummary summary) {
        return new AgentMessageResult(summary.agentId(), summary.turn(),
                summary.reason() == TurnEndReason.COMPLETED ? "COMPLETED" : summary.reason().name(),
                summary.lastAssistantText(), 0);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    /** 参数 map → JSON（Jackson：手搓转义不全会让 \U 等非法转义进流，严格解析即炸）。 */
    private static String toJson(java.util.Map<String, Object> args) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
        } catch (Exception e) {
            return String.valueOf(args);
        }
    }
}
