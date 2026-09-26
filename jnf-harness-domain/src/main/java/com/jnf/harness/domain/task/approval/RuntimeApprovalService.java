package com.jnf.harness.domain.task.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 运行期审批服务：高风险工具调用在此挂起，等待用户裁决。
 * <p>裁决语义：ALLOW_ONCE 放行一次 / ALLOW_SESSION 本会话该工具免审 /
 * DENY 拒绝 / CANCEL 取消；超时按 DENY 收口。全过程落审批记录（R10）。
 */
public class RuntimeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeApprovalService.class);

    /** 裁决类型。 */
    public enum Verdict {
        ALLOW_ONCE, ALLOW_SESSION, DENY, CANCEL
    }

    /** 挂起中的审批。 */
    public record PendingApproval(String id, String toolName, String argsJson, Instant createdAt) {
    }

    private record PendingState(PendingApproval approval, CompletableFuture<Verdict> future) {
    }

    private final IApprovalRecordRepository repository;
    private final Map<String, PendingState> pending = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionAllowed = new ConcurrentHashMap<>();

    public RuntimeApprovalService(IApprovalRecordRepository repository) {
        this.repository = repository;
    }

    /** 发起审批请求（阻塞方由此拿 future 等裁决）。 */
    public PendingApproval request(String sessionId, String toolName, String argsJson) {
        String id = UUID.randomUUID().toString();
        PendingApproval approval = new PendingApproval(id, toolName, argsJson, Instant.now());
        pending.put(id, new PendingState(approval, new CompletableFuture<>()));
        repository.insert(new ApprovalRecordEntity(id, sessionId, toolName, argsJson,
                ApprovalRecordEntity.VERDICT_PENDING, approval.createdAt(), null));
        log.info("运行期审批挂起: tool={} id={}", toolName, id);
        return approval;
    }

    /** 等待裁决的信号。 */
    public CompletableFuture<Verdict> decision(String approvalId) {
        PendingState state = pending.get(approvalId);
        return state == null ? CompletableFuture.completedFuture(Verdict.DENY) : state.future();
    }

    /** 提交裁决（UI/REST 层）。 */
    public void resolve(String approvalId, Verdict verdict) {
        PendingState state = pending.remove(approvalId);
        if (state == null) {
            return;
        }
        String sessionKey = state.approval().toolName();
        if (verdict == Verdict.ALLOW_SESSION) {
            sessionAllowed.computeIfAbsent(sessionKey, k -> new CopyOnWriteArraySet<>())
                    .add(state.approval().toolName());
        }
        repository.updateVerdict(approvalId, verdict.name());
        state.future().complete(verdict);
        log.info("运行期审批裁决: tool={} id={} verdict={}", state.approval().toolName(), approvalId, verdict);
    }

    /** 本会话该工具是否已获免审。 */
    public boolean sessionAllowed(String toolName) {
        Set<String> allowed = sessionAllowed.get(toolName);
        return allowed != null && allowed.contains(toolName);
    }

    /** 清除会话免审（切换会话时调用）。 */
    public void clearSessionAllowances() {
        sessionAllowed.clear();
    }

    public List<PendingApproval> pendingApprovals() {
        List<PendingApproval> result = new ArrayList<>();
        pending.values().forEach(state -> result.add(state.approval()));
        return result;
    }

    /** 超时收口：把未决请求按 TIMEOUT 结案。 */
    public void timeout(String approvalId) {
        PendingState state = pending.remove(approvalId);
        if (state != null) {
            repository.updateVerdict(approvalId, ApprovalRecordEntity.VERDICT_TIMEOUT);
            state.future().complete(Verdict.DENY);
        }
    }
}
