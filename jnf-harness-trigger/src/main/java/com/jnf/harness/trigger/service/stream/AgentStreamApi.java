package com.jnf.harness.trigger.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.api.IAgentStreamApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.stream.AgentStreamSink;
import com.jnf.harness.model.UsageReport;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 流式推送：把 case 层 sink 事件映射为 SSE 帧（meta/chunk/thinking/step_break/tool_result/usage/finish/done/error）。
 * <p>工程细节：超时不限（SSE_TIMEOUT 0）；15s 注释帧心跳防代理/看门狗断流；终态后关闭。
 */
public class AgentStreamApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long KEEPALIVE_SECONDS = 15;

    private final IAgentStreamApi useCase;
    private final ScheduledExecutorService keepalivePool;

    public AgentStreamApi(IAgentStreamApi useCase) {
        this.useCase = useCase;
        this.keepalivePool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "sse-keepalive");
            thread.setDaemon(true);
            return thread;
        });
    }

    public SseEmitter stream(AgentMessageCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        StreamSession session = new StreamSession(emitter);
        emitter.onCompletion(session::close);
        emitter.onTimeout(session::close);
        emitter.onError(e -> session.close());
        useCase.stream(command, session);
        return emitter;
    }

    /**
     * 入参校验失败：用同形状的 SSE 流把错误送回前端。
     * <p>流式接口失败在握手之后，无法再改 HTTP 状态码，只能发一帧 error（前端已按该事件处理）。
     */
    public SseEmitter fail(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        StreamSession session = new StreamSession(emitter);
        emitter.onCompletion(session::close);
        emitter.onTimeout(session::close);
        emitter.onError(e -> session.close());
        session.onError("INVALID_ARGUMENT", message);
        return emitter;
    }

    /** 一条 SSE 流的会话：sink 实现 + 心跳 + 终态关闭（幂等）。 */
    private final class StreamSession implements AgentStreamSink {
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final ScheduledFuture<?> keepalive;

        StreamSession(SseEmitter emitter) {
            this.emitter = emitter;
            this.keepalive = keepalivePool.scheduleAtFixedRate(
                    this::sendKeepalive, KEEPALIVE_SECONDS, KEEPALIVE_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void onMeta(String agentId) {
            send("meta", Map.of("agentId", agentId));
        }

        @Override
        public void onChunk(String content) {
            send("chunk", Map.of("content", content));
        }

        @Override
        public void onReasoning(String content) {
            // P9-8：思考内容流（前端折叠展示）
            send("thinking", Map.of("content", content));
        }

        @Override
        public void onStepBreak(String toolName, String callId, String argsJson, String status) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", toolName);
            payload.put("callId", callId);
            payload.put("args", argsJson);
            payload.put("status", status);
            send("step_break", payload);
        }

        @Override
        public void onToolResult(String toolName, String callId, String result, boolean error) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", toolName);
            payload.put("callId", callId);
            payload.put("result", result);
            payload.put("error", error);
            send("tool_result", payload);
        }

        @Override
        public void onFinish(long turn) {
            send("finish", Map.of("turn", turn));
        }

        @Override
        public void onUsage(UsageReport report) {
            // P11-5：用量帧。累计量给状态栏，速度/耗时给「tok/s」显示，缓存命中率单独给口径
            if (report == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promptTokens", report.promptTokens());
            payload.put("completionTokens", report.completionTokens());
            payload.put("totalTokens", report.totalTokens());
            payload.put("cachedTokens", report.cachedTokens());
            payload.put("cacheHitRate", report.cacheHitRate());
            payload.put("llmCalls", report.llmCalls());
            payload.put("elapsedMs", report.lastElapsedMs());
            payload.put("tokensPerSecond", report.tokensPerSecond());
            // 最近一次调用的输入 token 数 = 当前上下文占用（累计 promptTokens 会越滚越大，不能当占用看）
            payload.put("lastPromptTokens", report.lastPromptTokens());
            send("usage", payload);
        }

        @Override
        public void onDone(String agentId, long turn, String status, String text, int toolCalls) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agentId", agentId);
            payload.put("turn", turn);
            payload.put("status", status);
            payload.put("text", text);
            payload.put("toolCalls", toolCalls);
            send("done", payload);
            close();
        }

        @Override
        public void onError(String code, String message) {
            send("error", Map.of("code", code, "message", message));
            close();
        }

        private void send(String event, Map<String, Object> payload) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(MAPPER.writeValueAsString(payload)));
            } catch (Exception e) {
                close();
            }
        }

        private void sendKeepalive() {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                close();
            }
        }

        /** 幂等关闭：停心跳 + 完成流。 */
        void close() {
            if (closed.compareAndSet(false, true)) {
                keepalive.cancel(false);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已关闭
                }
            }
        }
    }
}
