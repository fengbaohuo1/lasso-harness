package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.task.HarnessTaskEntity;
import com.jnf.harness.domain.task.TaskSubmitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 任务提交与审批（任务主线 REST）。
 */
@RestController
@RequestMapping("/api/harness")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.task", havingValue = "true", matchIfMissing = true)
public class HarnessTaskController {

    private final TaskSubmitService taskSubmitService;

    public HarnessTaskController(TaskSubmitService taskSubmitService) {
        this.taskSubmitService = taskSubmitService;
    }

    /** 提交任务：{"sessionId","prompt","enabledTools":[...]} */
    @PostMapping("/tasks/submit")
    public Response<HarnessTaskEntity> submit(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> tools = body.get("enabledTools") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return Response.success(taskSubmitService.submit(
                String.valueOf(body.getOrDefault("sessionId", "task-session")),
                String.valueOf(body.getOrDefault("prompt", "")), tools));
    }

    @GetMapping("/tasks/{taskId}")
    public Response<HarnessTaskEntity> get(@PathVariable String taskId) {
        return Response.success(taskSubmitService.get(taskId));
    }

    /** 审批通过：PENDING_APPROVAL → QUEUED → 执行。 */
    @PostMapping("/tasks/{taskId}/approve")
    public Response<HarnessTaskEntity> approve(@PathVariable String taskId) {
        return Response.success(taskSubmitService.approve(taskId));
    }
}
