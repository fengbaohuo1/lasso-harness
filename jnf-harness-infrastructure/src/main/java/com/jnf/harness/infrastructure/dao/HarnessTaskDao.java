package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.HarnessTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 任务队列 DAO（harness_task）。
 */
@Mapper
public interface HarnessTaskDao {

    @Insert("INSERT INTO harness_task (task_id, session_id, prompt, enabled_tools_json, status, submitted_at) "
            + "VALUES (#{taskId}, #{sessionId}, #{prompt}, #{enabledToolsJson}, #{status}, CURRENT_TIMESTAMP)")
    void insert(HarnessTaskPO po);

    @Update("UPDATE harness_task SET status = #{status} WHERE task_id = #{taskId}")
    void updateStatus(@Param("taskId") String taskId, @Param("status") String status);

    @Select("SELECT * FROM harness_task WHERE task_id = #{taskId}")
    HarnessTaskPO findByCode(@Param("taskId") String taskId);

    @Select("SELECT * FROM harness_task ORDER BY submitted_at")
    List<HarnessTaskPO> findAll();
}
