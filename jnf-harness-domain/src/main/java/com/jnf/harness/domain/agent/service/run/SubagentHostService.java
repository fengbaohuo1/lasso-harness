package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.service.Inbox;
import com.jnf.harness.spi.host.AgentRunService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 子智能体宿主实现（P8-3）：{@link AgentRunService} 的宿主端。
 * <ul>
 *   <li>独立子会话（sub-&lt;uuid&gt;）经 {@link AgentRunFactory} 复用既有装配跑一轮；</li>
 *   <li>递归防护：子会话深度 = 父深度 + 1，超过 {@value #MAX_DEPTH} 拒绝（防止工具自举爆炸）；</li>
 *   <li>超时收口：默认 {@value #DEFAULT_TIMEOUT_SECONDS}s，超时即失败返回（不挂死父回合）；</li>
 *   <li>深度表持久保留（子会话 id → 深度）：可观测/可断言，子会话结束后仍能追溯嵌套关系。</li>
 * </ul>
 */
public class SubagentHostService implements AgentRunService {

    static final int MAX_DEPTH = 2;
    static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final AgentRunFactory agentRunFactory;
    /** 子会话 id → 深度（顶层会话不在表内 = 深度 0）；持久保留（规模失控时整体清空兜底）。 */
    private final Map<String, Integer> depthBySession = new ConcurrentHashMap<>();

    public SubagentHostService(AgentRunFactory agentRunFactory) {
        this.agentRunFactory = agentRunFactory;
    }

    @Override
    public SubagentOutcome run(SubagentRequest request) {
        if (request == null || request.task() == null || request.task().isBlank()) {
            return new SubagentOutcome(false, null, null, 0, "task 不能为空");
        }
        String parent = request.parentSessionId() == null ? "" : request.parentSessionId();
        int depth = depthBySession.getOrDefault(parent, 0) + 1;
        if (depth > MAX_DEPTH) {
            return new SubagentOutcome(false, null, null, 0,
                    "子智能体嵌套过深（深度 " + depth + " > " + MAX_DEPTH + "），拒绝递归派发");
        }
        String sessionId = "sub-" + java.util.UUID.randomUUID();
        if (depthBySession.size() > 10_000) {
            depthBySession.clear();
        }
        depthBySession.put(sessionId, depth);
        try {
            ReactLoopAgent agent = agentRunFactory.getOrCreate(sessionId, null);
            String prompt = request.context() == null || request.context().isBlank()
                    ? request.task()
                    : request.context().trim() + "\n\n【子任务】" + request.task();
            int timeout = request.timeoutSeconds() > 0 ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
            Inbox.TurnSummary summary = agent
                    .ask(prompt, null, request.profile(), null)
                    .get(timeout, TimeUnit.SECONDS);
            return new SubagentOutcome(true, sessionId,
                    summary.lastAssistantText(), summary.turn(), null);
        } catch (TimeoutException e) {
            return new SubagentOutcome(false, sessionId, null, 0,
                    "子智能体执行超时（" + timeoutOf(request) + "s）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SubagentOutcome(false, sessionId, null, 0, "子智能体执行被中断");
        } catch (Exception e) {
            return new SubagentOutcome(false, sessionId, null, 0,
                    "子智能体执行失败: " + e.getMessage());
        }
    }

    private static int timeoutOf(SubagentRequest request) {
        return request.timeoutSeconds() > 0 ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
    }

    /** 当前子会话深度（治理可观测/测试断言用）。 */
    public int depthOf(String sessionId) {
        return depthBySession.getOrDefault(sessionId, 0);
    }
}
