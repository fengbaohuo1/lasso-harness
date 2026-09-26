package com.jnf.harness.domain.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 调度服务（schedule 域，修正项 R7：记录持久化）。
 * <p>执行引擎（定时触发）挂账——本服务负责记录与状态机（SCHEDULED/CANCELED）。
 */
public class ScheduleService {

    public record ScheduleRecord(String scheduleId, String kind, String name, String spec,
                                 String state, Instant createdAt) {
    }

    public interface IScheduleRepository {
        void save(ScheduleRecord record);

        Optional<ScheduleRecord> findById(String scheduleId);

        List<ScheduleRecord> findAll();
    }

    private final IScheduleRepository repository;

    public ScheduleService(IScheduleRepository repository) {
        this.repository = repository;
    }

    public ScheduleRecord create(String kind, String name, String spec) {
        ScheduleRecord record = new ScheduleRecord(UUID.randomUUID().toString(),
                kind == null ? "INTERVAL" : kind, name, spec, "SCHEDULED", Instant.now());
        repository.save(record);
        return record;
    }

    public ScheduleRecord cancel(String scheduleId) {
        ScheduleRecord record = repository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("调度不存在: " + scheduleId));
        ScheduleRecord canceled = new ScheduleRecord(record.scheduleId(), record.kind(),
                record.name(), record.spec(), "CANCELED", record.createdAt());
        repository.save(canceled);
        return canceled;
    }

    public List<ScheduleRecord> list() {
        return repository.findAll();
    }
}
