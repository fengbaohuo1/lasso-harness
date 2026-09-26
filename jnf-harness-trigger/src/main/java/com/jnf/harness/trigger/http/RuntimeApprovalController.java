package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService.PendingApproval;
import com.jnf.harness.domain.task.approval.RuntimeApprovalService.Verdict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运行期审批（挂起-裁决轮询面）。
 */
@RestController
@RequestMapping("/api/harness/approvals/runtime")
public class RuntimeApprovalController {

    private final RuntimeApprovalService service;

    public RuntimeApprovalController(RuntimeApprovalService service) {
        this.service = service;
    }

    /** 待裁决列表（前端轮询）。 */
    @GetMapping("/pending")
    public Response<List<PendingApproval>> pending() {
        return Response.success(service.pendingApprovals());
    }

    /** 提交裁决：{"verdict":"ALLOW_ONCE|ALLOW_SESSION|DENY|CANCEL"} */
    @PostMapping("/{approvalId}/resolve")
    public Response<?> resolve(@PathVariable String approvalId, @RequestBody Map<String, Object> body) {
        String raw = String.valueOf(body.getOrDefault("verdict", "DENY"));
        Verdict verdict;
        try {
            verdict = Verdict.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument("verdict 只能是 ALLOW_ONCE/ALLOW_SESSION/DENY/CANCEL");
        }
        service.resolve(approvalId, verdict);
        return Response.success(Map.of("approvalId", approvalId, "verdict", verdict.name()));
    }
}
