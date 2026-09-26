package com.jnf.harness.domain.task;

import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提交过滤链（submission 子域）+ 权限矩阵评估（permission 子域）。
 */
public class TaskSubmissionPolicyService {

    private final IToolCatalogPort toolCatalogPort;

    public TaskSubmissionPolicyService(IToolCatalogPort toolCatalogPort) {
        this.toolCatalogPort = toolCatalogPort;
    }

    /** 过滤 + 校验：prompt 非空、工具集只留目录内启用工具。 */
    public HarnessTaskEntity filter(HarnessTaskEntity task) {
        if (task.prompt() == null || task.prompt().isBlank()) {
            throw new IllegalArgumentException("prompt 必填");
        }
        Set<String> enabled = toolCatalogPort.findEnabled("default").stream()
                .map(IToolCatalogPort.ToolCatalogEntry::toolCode)
                .collect(Collectors.toSet());
        List<String> tools = new ArrayList<>();
        for (String tool : task.enabledTools()) {
            if (enabled.isEmpty() || enabled.contains(tool)) {
                tools.add(tool);
            }
        }
        return new HarnessTaskEntity(task.taskId(), task.sessionId(), task.prompt().trim(), tools, task.status());
    }
}
