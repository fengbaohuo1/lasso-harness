package com.jnf.harness.domain.task;

import java.util.List;
import java.util.Optional;

/**
 * 任务队列仓储端口（queue 子域）。
 */
public interface IHarnessTaskQueueRepository {

    void insert(HarnessTaskEntity task);

    void updateStatus(String taskId, TaskStatus status);

    Optional<HarnessTaskEntity> findById(String taskId);

    List<HarnessTaskEntity> findAll();
}
