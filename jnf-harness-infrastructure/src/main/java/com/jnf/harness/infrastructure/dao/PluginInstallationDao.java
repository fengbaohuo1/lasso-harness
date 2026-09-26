package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.PluginInstallationPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 插件安装记录 DAO（harness_plugin_installation）。
 */
@Mapper
public interface PluginInstallationDao {

    @Insert("INSERT INTO harness_plugin_installation "
            + "(plugin_id, display_name, plugin_version, runtime_type, source_path, entrypoint, status, installed_at) "
            + "VALUES (#{pluginId}, #{displayName}, #{pluginVersion}, #{runtimeType}, #{sourcePath}, "
            + "#{entrypoint}, #{status}, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), plugin_version = VALUES(plugin_version), "
            + "runtime_type = VALUES(runtime_type), source_path = VALUES(source_path), "
            + "entrypoint = VALUES(entrypoint), status = VALUES(status)")
    void upsert(PluginInstallationPO po);

    @Update("UPDATE harness_plugin_installation SET status = #{status} WHERE plugin_id = #{pluginId}")
    void updateStatus(@Param("pluginId") String pluginId, @Param("status") String status);

    @Delete("DELETE FROM harness_plugin_installation WHERE plugin_id = #{pluginId}")
    void deleteByCode(@Param("pluginId") String pluginId);

    @Select("SELECT * FROM harness_plugin_installation ORDER BY plugin_id")
    List<PluginInstallationPO> findAll();

    @Select("SELECT * FROM harness_plugin_installation WHERE plugin_id = #{pluginId}")
    PluginInstallationPO findByCode(@Param("pluginId") String pluginId);
}
