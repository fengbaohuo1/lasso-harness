package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.ApprovalRecordPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 审批记录 DAO（harness_approval_record，R10）。
 */
@Mapper
public interface ApprovalRecordDao {

    @Insert("INSERT INTO harness_approval_record (id, session_id, tool_name, args_json, verdict, created_at, decided_at) "
            + "VALUES (#{id}, #{sessionId}, #{toolName}, #{argsJson}, #{verdict}, CURRENT_TIMESTAMP, NULL)")
    void insert(ApprovalRecordPO po);

    @Update("UPDATE harness_approval_record SET verdict = #{verdict}, decided_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    void updateVerdict(@Param("id") String id, @Param("verdict") String verdict);

    @Select("SELECT * FROM harness_approval_record WHERE verdict = 'PENDING' ORDER BY created_at")
    List<ApprovalRecordPO> findPending();

    @Select("SELECT * FROM harness_approval_record ORDER BY created_at")
    List<ApprovalRecordPO> findAll();
}
