package com.jnf.harness.domain.task;

import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.agent.service.run.ReactLoopAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话执行（execution 子域）：任务驱动 Agent 跑一轮。
 * <p>防绕过（对齐参照语义）：PENDING_APPROVAL 状态直接拒绝执行——必须先审批。
 */
public class HarnessExecutionService {

    private static final Logger log = LoggerFactory.getLogger(HarnessExecutionService.class);
    private static final long EXECUTE_TIMEOUT_SECONDS = 300;

    private final IHarnessTaskQueueRepository queue;
    private final AgentRunFactory agentRunFactory;
    private final ExecutorService workers;

    public HarnessExecutionService(IHarnessTaskQueueRepository queue, AgentRunFactory agentRunFactory) {
        this.queue = queue;
        this.agentRunFactory = agentRunFactory;
        // R6：有界执行池
        this.workers = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "task-executor");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 执行任务（异步）；状态必须是 QUEUED。 */
    public void executeSession(String taskId) {
        HarnessTaskEntity task = queue.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (task.status() == TaskStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("PENDING_APPROVAL 状态必须先审批，禁止执行");
        }
        if (!task.status().executable()) {
            throw new IllegalStateException("任务状态不可执行: " + task.status());
        }
        queue.updateStatus(taskId, TaskStatus.RUNNING);
        workers.submit(() -> {
            try {
                ReactLoopAgent agent = agentRunFactory.getOrCreate(task.sessionId(), null);
                agent.ask(task.prompt()).get(EXECUTE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                queue.updateStatus(taskId, TaskStatus.COMPLETED);
                log.info("任务完成: {}", taskId);
            } catch (Exception e) {
                queue.updateStatus(taskId, TaskStatus.FAILED);
                log.warn("任务失败: {}: {}", taskId, e.getMessage());
            }
        });
    }
}
