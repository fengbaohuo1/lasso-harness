package com.jnf.harness.domain.task.approval;

import com.jnf.harness.domain.task.approval.RuntimeApprovalService.PendingApproval;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService.Verdict;
import com.jnf.harness.domain.tool.service.ApprovalGate;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * 运行期审批闸（修正项 R1：默认 REQUEST_APPROVAL，不再 allowAll）。
 * <p>高风险工具调用在此**挂起**：创建审批请求并阻塞等待裁决（超时按拒绝收口）。
 * <p>三档：REQUEST_APPROVAL（风险工具挂起）/ AUTO_APPROVE（全放行）。
 */
public class RuntimeApprovalGate implements ApprovalGate {

    /** 审批模式。 */
    public enum Mode {
        REQUEST_APPROVAL, AUTO_APPROVE
    }

    private final RuntimeApprovalService service;
    private final Mode mode;
    private final Predicate<String> riskyTool;
    private final String sessionId;
    private final long timeoutSeconds;

    public RuntimeApprovalGate(RuntimeApprovalService service, Mode mode,
                               Predicate<String> riskyTool, String sessionId, long timeoutSeconds) {
        this.service = service;
        this.mode = mode == null ? Mode.REQUEST_APPROVAL : mode;
        this.riskyTool = riskyTool == null ? tool -> false : riskyTool;
        this.sessionId = sessionId;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
    }

    @Override
    public boolean allow(String toolName, Map<String, Object> arguments) {
        if (mode == Mode.AUTO_APPROVE || !riskyTool.test(toolName) || service.sessionAllowed(toolName)) {
            return true;
        }
        // argsJson 必须是 JSON（Map.toString 会让审批卡/回放展示成 {k=v} 杂讯，前端无法解析）
        String argsJson;
        try {
            argsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(arguments);
        } catch (Exception e) {
            argsJson = String.valueOf(arguments);
        }
        PendingApproval pending = service.request(sessionId, toolName, argsJson);
        try {
            Verdict verdict = service.decision(pending.id()).get(timeoutSeconds, TimeUnit.SECONDS);
            return verdict == Verdict.ALLOW_ONCE || verdict == Verdict.ALLOW_SESSION;
        } catch (TimeoutException e) {
            service.timeout(pending.id());
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
