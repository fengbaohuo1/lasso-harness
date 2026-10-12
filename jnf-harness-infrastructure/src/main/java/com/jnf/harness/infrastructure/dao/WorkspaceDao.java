package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.WorkspacePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工作区 DAO（harness_workspace，P9-1）。
 */
@Mapper
public interface WorkspaceDao {

    String COLUMNS = "workspace_code, display_name, root_path ";

    @Select("SELECT " + COLUMNS + "FROM harness_workspace ORDER BY workspace_code")
    List<WorkspacePO> findAll();

    @Select("SELECT " + COLUMNS + "FROM harness_workspace WHERE workspace_code = #{workspaceCode}")
    WorkspacePO findByCode(@Param("workspaceCode") String workspaceCode);

    @Insert("INSERT INTO harness_workspace (workspace_code, display_name, root_path) "
            + "VALUES (#{workspaceCode}, #{displayName}, #{rootPath})")
    int insert(WorkspacePO po);

    @Update("UPDATE harness_workspace SET display_name = #{displayName}, root_path = #{rootPath} "
            + "WHERE workspace_code = #{workspaceCode}")
    int update(WorkspacePO po);

    @Delete("DELETE FROM harness_workspace WHERE workspace_code = #{workspaceCode}")
    int delete(@Param("workspaceCode") String workspaceCode);
}
