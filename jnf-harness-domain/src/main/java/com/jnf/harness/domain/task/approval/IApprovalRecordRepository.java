package com.jnf.harness.domain.task.approval;

import java.util.List;

/**
 * 审批记录仓储端口。
 */
public interface IApprovalRecordRepository {

    void insert(ApprovalRecordEntity record);

    void updateVerdict(String id, String verdict);

    List<ApprovalRecordEntity> findPending();

    List<ApprovalRecordEntity> findAll();
}
