package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.ToolCatalogPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工具目录 DAO（harness_tool_catalog + harness_tool_profile_binding，R5 真接线）。
 */
@Mapper
public interface ToolCatalogDao {

    @Select("SELECT t.tool_code, t.display_name, t.description, t.category, t.requires_approval "
            + "FROM harness_tool_catalog t "
            + "JOIN harness_tool_profile_binding b ON t.tool_code = b.tool_code "
            + "WHERE b.profile_code = #{profileCode} ORDER BY t.tool_code")
    List<ToolCatalogPO> findEnabled(@Param("profileCode") String profileCode);

    @Select("SELECT tool_code, display_name, description, category, requires_approval "
            + "FROM harness_tool_catalog ORDER BY tool_code")
    List<ToolCatalogPO> findAll();
}
