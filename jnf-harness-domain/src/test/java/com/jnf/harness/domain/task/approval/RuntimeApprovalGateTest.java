package com.jnf.harness.domain.task.approval;

import com.jnf.harness.domain.task.approval.RuntimeApprovalService.PendingApproval;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService.Verdict;
import com.jnf.harness.domain.task.approval.RuntimeApprovalGate.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行期审批语义（R1 DoD）：挂起→ALLOW/DENY、会话免审、超时拒绝、AUTO_APPROVE。
 */
class RuntimeApprovalGateTest {

    /** 内存审批记录（测试替身）。 */
    static final class InMemoryRepo implements IApprovalRecordRepository {
        final List<ApprovalRecordEntity> records = new CopyOnWriteArrayList<>();

        @Override
        public void insert(ApprovalRecordEntity record) {
            records.add(record);
        }

        @Override
        public void updateVerdict(String id, String verdict) {
            records.replaceAll(r -> r.id().equals(id)
                    ? new ApprovalRecordEntity(r.id(), r.sessionId(), r.toolName(), r.argsJson(),
                    verdict, r.createdAt(), java.time.Instant.now()) : r);
        }

        @Override
        public List<ApprovalRecordEntity> findPending() {
            return records.stream().filter(r -> "PENDING".equals(r.verdict())).toList();
        }

        @Override
        public List<ApprovalRecordEntity> findAll() {
            return records;
        }
    }

    private RuntimeApprovalService service(InMemoryRepo repo) {
        return new RuntimeApprovalService(repo);
    }

    @Test
    @DisplayName("ALLOW_ONCE：挂起 → 裁决放行 → allow() 通过")
    void allow_once_unblocks() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        RuntimeApprovalService service = service(repo);
        RuntimeApprovalGate gate = new RuntimeApprovalGate(service, Mode.REQUEST_APPROVAL,
                "shell_execute"::equals, "s-1", 5);

        CompletableFuture<Boolean> decision = CompletableFuture.supplyAsync(
                () -> gate.allow("shell_execute", Map.of("command", "ls")));
        // 等挂起出现
        PendingApproval pending = waitForPending(service);
        service.resolve(pending.id(), Verdict.ALLOW_ONCE);

        assertTrue(decision.get());
        assertEquals(1, repo.records.size());
        assertEquals("ALLOW_ONCE", repo.records.get(0).verdict());
    }

    @Test
    @DisplayName("DENY：裁决拒绝 → allow() 不通过")
    void deny_blocks() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        RuntimeApprovalService service = service(repo);
        RuntimeApprovalGate gate = new RuntimeApprovalGate(service, Mode.REQUEST_APPROVAL,
                "shell_execute"::equals, "s-1", 5);

        CompletableFuture<Boolean> decision = CompletableFuture.supplyAsync(
                () -> gate.allow("shell_execute", Map.of("command", "rm")));
        service.resolve(waitForPending(service).id(), Verdict.DENY);

        assertFalse(decision.get());
    }

    @Test
    @DisplayName("ALLOW_SESSION：后续同工具免审")
    void session_allowance() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        RuntimeApprovalService service = service(repo);
        RuntimeApprovalGate gate = new RuntimeApprovalGate(service, Mode.REQUEST_APPROVAL,
                "shell_execute"::equals, "s-1", 5);

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                () -> gate.allow("shell_execute", Map.of()));
        service.resolve(waitForPending(service).id(), Verdict.ALLOW_SESSION);
        assertTrue(first.get());

        // 第二次不再挂起
        assertTrue(gate.allow("shell_execute", Map.of()));
        assertEquals(1, repo.records.size(), "会话免审后不应再产生审批记录");
    }

    @Test
    @DisplayName("超时按拒绝收口（TIMEOUT 落记录）；非风险工具与 AUTO_APPROVE 直通")
    void timeout_and_passthrough() {
        InMemoryRepo repo = new InMemoryRepo();
        RuntimeApprovalService service = service(repo);
        RuntimeApprovalGate gate = new RuntimeApprovalGate(service, Mode.REQUEST_APPROVAL,
                "shell_execute"::equals, "s-1", 1);

        assertFalse(gate.allow("shell_execute", Map.of()), "超时应拒绝");
        assertEquals("TIMEOUT", repo.records.get(0).verdict());

        assertTrue(gate.allow("fs_read", Map.of()), "非风险工具直通");

        RuntimeApprovalGate auto = new RuntimeApprovalGate(service, Mode.AUTO_APPROVE,
                "shell_execute"::equals, "s-1", 1);
        assertTrue(auto.allow("shell_execute", Map.of()), "AUTO_APPROVE 直通");
    }

    private PendingApproval waitForPending(RuntimeApprovalService service) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (!service.pendingApprovals().isEmpty()) {
                return service.pendingApprovals().get(0);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("审批未挂起");
    }
}
