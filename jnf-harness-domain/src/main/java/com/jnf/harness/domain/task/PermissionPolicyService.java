package com.jnf.harness.domain.task;

import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限矩阵评估（permission 子域）：工具集的风险面评估。
 */
public class PermissionPolicyService {

    /** 评估结论：风险工具清单 + 是否需要审批。 */
    public record PermissionAssessment(List<String> riskyTools, boolean requiresApproval) {
    }

    private final IToolCatalogPort toolCatalogPort;

    public PermissionPolicyService(IToolCatalogPort toolCatalogPort) {
        this.toolCatalogPort = toolCatalogPort;
    }

    public PermissionAssessment assess(List<String> toolCodes) {
        Map<String, Boolean> requiresApproval = toolCatalogPort.findAll().stream()
                .collect(Collectors.toMap(IToolCatalogPort.ToolCatalogEntry::toolCode,
                        IToolCatalogPort.ToolCatalogEntry::requiresApproval, (a, b) -> a));
        Set<String> catalog = requiresApproval.keySet();
        List<String> risky = new ArrayList<>();
        for (String code : toolCodes) {
            if (catalog.isEmpty()) {
                continue;
            }
            if (Boolean.TRUE.equals(requiresApproval.get(code))) {
                risky.add(code);
            }
        }
        return new PermissionAssessment(risky, !risky.isEmpty());
    }
}
