package com.jnf.harness.app;

import com.jnf.harness.domain.task.approval.RuntimeApprovalService;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService.Verdict;
import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 接线端到端：ToolCallExecutor 真走运行期审批闸（挂起→REST 可裁决语义）。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:apprtest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "harness.approval.timeout-seconds=5"})
class RuntimeApprovalIntegrationTest {

    @Autowired
    ToolRunner toolRunner;

    @Autowired
    RuntimeApprovalService approvalService;

    @Test
    @DisplayName("fs_write（目录标记需审批）挂起 → ALLOW_ONCE 放行执行")
    void risky_tool_requires_approval_and_allows() throws Exception {
        CompletableFuture<ToolExecutionResult> call = toolRunner.run(
                new ToolCallBlock("c1", "fs_write", Map.of("path", "approved.txt", "content", "ok")));

        RuntimeApprovalService.PendingApproval pending = waitForPending();
        assertEquals("fs_write", pending.toolName());
        approvalService.resolve(pending.id(), Verdict.ALLOW_ONCE);

        ToolExecutionResult result = call.get();
        assertTrue(result.success(), result.content());
    }

    @Test
    @DisplayName("DENY → 结构化拒绝（APPROVAL_REQUIRED）")
    void deny_yields_structured_failure() throws Exception {
        CompletableFuture<ToolExecutionResult> call = toolRunner.run(
                new ToolCallBlock("c2", "fs_write", Map.of("path", "denied.txt", "content", "x")));

        approvalService.resolve(waitForPending().id(), Verdict.DENY);

        ToolExecutionResult result = call.get();
        assertFalse(result.success());
        assertEquals("APPROVAL_REQUIRED", result.errorCode());
    }

    private RuntimeApprovalService.PendingApproval waitForPending() throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            if (!approvalService.pendingApprovals().isEmpty()) {
                return approvalService.pendingApprovals().get(0);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("审批未挂起（R1 接线失效？）");
    }
}
