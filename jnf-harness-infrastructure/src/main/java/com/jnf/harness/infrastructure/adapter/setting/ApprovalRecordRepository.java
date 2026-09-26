package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.task.approval.ApprovalRecordEntity;
import com.jnf.harness.domain.task.approval.IApprovalRecordRepository;
import com.jnf.harness.infrastructure.dao.ApprovalRecordDao;
import com.jnf.harness.infrastructure.dao.po.ApprovalRecordPO;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 审批记录仓储（MyBatis 实现，R10）。
 */
public class ApprovalRecordRepository implements IApprovalRecordRepository {

    private final ApprovalRecordDao dao;

    public ApprovalRecordRepository(ApprovalRecordDao dao) {
        this.dao = dao;
    }

    @Override
    public void insert(ApprovalRecordEntity record) {
        ApprovalRecordPO po = new ApprovalRecordPO();
        po.setId(record.id());
        po.setSessionId(record.sessionId());
        po.setToolName(record.toolName());
        po.setArgsJson(record.argsJson());
        po.setVerdict(record.verdict());
        dao.insert(po);
    }

    @Override
    public void updateVerdict(String id, String verdict) {
        dao.updateVerdict(id, verdict);
    }

    @Override
    public List<ApprovalRecordEntity> findPending() {
        return dao.findPending().stream().map(ApprovalRecordRepository::toEntity).toList();
    }

    @Override
    public List<ApprovalRecordEntity> findAll() {
        return dao.findAll().stream().map(ApprovalRecordRepository::toEntity).toList();
    }

    private static ApprovalRecordEntity toEntity(ApprovalRecordPO po) {
        Instant created = po.getCreatedAt() == null ? null : po.getCreatedAt().toInstant();
        Instant decided = po.getDecidedAt() == null ? null : po.getDecidedAt().toInstant();
        return new ApprovalRecordEntity(po.getId(), po.getSessionId(), po.getToolName(),
                po.getArgsJson(), po.getVerdict(), created, decided);
    }
}
