package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.task.HarnessTaskEntity;
import com.jnf.harness.domain.task.IHarnessTaskQueueRepository;
import com.jnf.harness.domain.task.TaskStatus;
import com.jnf.harness.infrastructure.dao.HarnessTaskDao;
import com.jnf.harness.infrastructure.dao.po.HarnessTaskPO;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 任务队列仓储（MyBatis 实现；enabledTools 逗号串存储）。
 */
public class HarnessTaskRepository implements IHarnessTaskQueueRepository {

    private final HarnessTaskDao dao;

    public HarnessTaskRepository(HarnessTaskDao dao) {
        this.dao = dao;
    }

    @Override
    public void insert(HarnessTaskEntity task) {
        HarnessTaskPO po = new HarnessTaskPO();
        po.setTaskId(task.taskId());
        po.setSessionId(task.sessionId());
        po.setPrompt(task.prompt());
        po.setEnabledToolsJson(String.join(",", task.enabledTools()));
        po.setStatus(task.status().name());
        dao.insert(po);
    }

    @Override
    public void updateStatus(String taskId, TaskStatus status) {
        dao.updateStatus(taskId, status.name());
    }

    @Override
    public Optional<HarnessTaskEntity> findById(String taskId) {
        return Optional.ofNullable(dao.findByCode(taskId)).map(HarnessTaskRepository::toEntity);
    }

    @Override
    public List<HarnessTaskEntity> findAll() {
        return dao.findAll().stream().map(HarnessTaskRepository::toEntity).toList();
    }

    private static HarnessTaskEntity toEntity(HarnessTaskPO po) {
        List<String> tools = po.getEnabledToolsJson() == null || po.getEnabledToolsJson().isBlank()
                ? List.of()
                : Arrays.stream(po.getEnabledToolsJson().split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
        return new HarnessTaskEntity(po.getTaskId(), po.getSessionId(), po.getPrompt(),
                tools, TaskStatus.valueOf(po.getStatus()));
    }
}
