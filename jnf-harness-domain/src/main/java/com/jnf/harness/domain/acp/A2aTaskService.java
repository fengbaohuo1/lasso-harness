package com.jnf.harness.domain.acp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A 任务注册（JSON-RPC 面的任务快照；进程内存——重启即失，与参照取舍一致）。
 */
public class A2aTaskService {

    /** 任务快照。 */
    public record A2aTask(String taskId, String contextId, String state, String output,
                          Instant createdAt) {
    }

    private final Map<String, A2aTask> tasks = new ConcurrentHashMap<>();

    public A2aTask create(String contextId) {
        String taskId = UUID.randomUUID().toString();
        A2aTask task = new A2aTask(taskId, contextId, "submitted", "", Instant.now());
        tasks.put(taskId, task);
        return task;
    }

    public A2aTask update(String taskId, String state, String output) {
        A2aTask current = tasks.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        A2aTask updated = new A2aTask(taskId, current.contextId(), state, output, current.createdAt());
        tasks.put(taskId, updated);
        return updated;
    }

    public A2aTask get(String taskId) {
        A2aTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    public List<A2aTask> list() {
        return new ArrayList<>(tasks.values());
    }
}
