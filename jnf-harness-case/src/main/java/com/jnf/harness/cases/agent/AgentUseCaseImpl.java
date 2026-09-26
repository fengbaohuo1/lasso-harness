package com.jnf.harness.cases.agent;

import com.jnf.harness.api.IAgentApi;
import com.jnf.harness.api.IAgentStreamApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.dto.AgentMessageResult;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.api.stream.AgentStreamSink;
import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.domain.agent.service.run.AgentEventListener;
import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;
import com.jnf.harness.domain.session.event.TurnEndReason;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;

import java.util.concurrent.TimeUnit;

/**
 * Agent 用例（薄用例形态）：命令校验 + 领域服务委托。
 * <p>第 7 节将对话链改造成策略树（Resolve→Intent→Dispatch→Collect）时保持本类对外签名不变。
 */
public class AgentUseCaseImpl implements IAgentApi, IAgentStreamApi {

    private static final long SEND_TIMEOUT_SECONDS = 300;

    private final AgentRunFactory factory;

    public AgentUseCaseImpl(AgentRunFactory factory) {
        this.factory = factory;
    }

    @Override
    public Response<AgentMessageResult> send(AgentMessageCommand command) {
        try {
            ReactLoopAgent agent = factory.getOrCreate(command.agentId(), null);
            Inbox.TurnSummary summary = agent.ask(command.message())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Response.success(toResult(summary));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        } catch (Exception e) {
            return Response.internalError("对话失败: " + rootMessage(e));
        }
    }

    @Override
    public Response<AgentMessageResult> status(String agentId) {
        return factory.get(agentId)
                .map(agent -> Response.success(new AgentMessageResult(
                        agentId, 0, agent.phase().getClass().getSimpleName().toUpperCase(),
                        "", (int) agent.meter().llmCalls())))
                .orElseGet(() -> Response.success(
                        new AgentMessageResult(agentId, 0, "UNKNOWN", "", 0)));
    }

    @Override
    public Response<Void> cancel(String agentId) {
        factory.get(agentId).ifPresent(ReactLoopAgent::cancel);
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
                sink.onFinish(summary.turn());
                sink.onDone(summary.agentId(), summary.turn(), summary.reason().name(),
                        summary.lastAssistantText(), toolCalls);
            }
        };
        try {
            ReactLoopAgent agent = factory.getOrCreate(command.agentId(), null);
            agent.ask(command.message(), listener)
                    .whenComplete((summary, error) -> {
                        if (error != null) {
                            sink.onError("TURN_ERROR", rootMessage(error));
                        }
                    });
        } catch (Exception e) {
            sink.onError("STREAM_ERROR", rootMessage(e));
        }
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

    /** 参数 map → 紧凑 JSON（仅工具参数这类扁平结构）。 */
    private static String toJson(java.util.Map<String, Object> args) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : args.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey().replace("\"", "\\\"")).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
