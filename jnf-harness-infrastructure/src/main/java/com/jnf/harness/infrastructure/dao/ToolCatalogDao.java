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

    /** 幂等登记（存在即保留管理员配置）；H2(MySQL 模式)/MySQL 双兼容的 insert-if-absent。 */
    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval) "
            + "SELECT #{toolCode}, #{displayName}, #{description}, #{category}, #{requiresApproval} FROM DUAL "
            + "WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = #{toolCode})")
    int insertIfAbsent(@Param("toolCode") String toolCode,
                       @Param("displayName") String displayName,
                       @Param("description") String description,
                       @Param("category") String category,
                       @Param("requiresApproval") boolean requiresApproval);

    /** 幂等绑定画像。 */
    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO harness_tool_profile_binding (profile_code, tool_code) "
            + "SELECT #{profileCode}, #{toolCode} FROM DUAL "
            + "WHERE NOT EXISTS (SELECT 1 FROM harness_tool_profile_binding "
            + "WHERE profile_code = #{profileCode} AND tool_code = #{toolCode})")
    int bindIfAbsent(@Param("profileCode") String profileCode, @Param("toolCode") String toolCode);
}
