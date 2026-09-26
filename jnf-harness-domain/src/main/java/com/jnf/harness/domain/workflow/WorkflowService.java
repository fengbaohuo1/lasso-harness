package com.jnf.harness.domain.workflow;

import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工作流服务（workflow 域，顺序步骤执行版）：每步驱动 Agent 跑一轮，逐步推进。
 */
public class WorkflowService {

    /** 工作流运行。 */
    public record WorkflowRun(String runId, List<String> steps, int currentStep,
                              String status, String lastOutput) {
    }

    private final AgentRunFactory agentRunFactory;
    private final Map<String, WorkflowRun> runs = new ConcurrentHashMap<>();
    private final Map<String, Boolean> canceled = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "workflow-engine");
        thread.setDaemon(true);
        return thread;
    });

    public WorkflowService(AgentRunFactory agentRunFactory) {
        this.agentRunFactory = agentRunFactory;
    }

    /** 启动顺序工作流（异步）。 */
    public WorkflowRun start(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps 必填");
        }
        String runId = UUID.randomUUID().toString();
        List<String> copy = List.copyOf(steps);
        WorkflowRun created = new WorkflowRun(runId, copy, 0, "RUNNING", "");
        runs.put(runId, created);
        workers.submit(() -> {
            String lastOutput = "";
            for (int i = 0; i < copy.size(); i++) {
                if (Boolean.TRUE.equals(canceled.get(runId))) {
                    runs.put(runId, new WorkflowRun(runId, copy, i, "CANCELED", lastOutput));
                    return;
                }
                runs.put(runId, new WorkflowRun(runId, copy, i, "RUNNING", lastOutput));
                try {
                    ReactLoopAgent agent = agentRunFactory.getOrCreate("workflow-agent", null);
                    var summary = agent.ask("工作流步骤 " + (i + 1) + ": " + copy.get(i))
                            .get(300, java.util.concurrent.TimeUnit.SECONDS);
                    lastOutput = summary.lastAssistantText();
                } catch (Exception e) {
                    runs.put(runId, new WorkflowRun(runId, copy, i + 1, "FAILED",
                            String.valueOf(e.getMessage())));
                    return;
                }
            }
            runs.put(runId, new WorkflowRun(runId, copy, copy.size(), "COMPLETED", lastOutput));
        });
        return created;
    }

    public WorkflowRun get(String runId) {
        WorkflowRun run = runs.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("工作流不存在: " + runId);
        }
        return run;
    }

    public void cancel(String runId) {
        canceled.put(runId, true);
    }

    public List<WorkflowRun> list() {
        return new ArrayList<>(runs.values());
    }
}
