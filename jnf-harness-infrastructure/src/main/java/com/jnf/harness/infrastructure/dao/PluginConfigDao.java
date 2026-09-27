package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.PluginConfigPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 插件配置 DAO（harness_plugin_config）。
 */
@Mapper
public interface PluginConfigDao {

    @Insert("INSERT INTO harness_plugin_config (plugin_id, config_key, config_layer, config_value, updated_at) "
            + "VALUES (#{pluginId}, #{configKey}, #{configLayer}, #{configValue}, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = CURRENT_TIMESTAMP")
    void upsert(PluginConfigPO po);

    @Delete("DELETE FROM harness_plugin_config WHERE plugin_id = #{pluginId} "
            + "AND config_key = #{configKey} AND config_layer = #{configLayer}")
    void deleteByKey(@Param("pluginId") String pluginId, @Param("configKey") String configKey,
                     @Param("configLayer") String configLayer);

    @Select("SELECT * FROM harness_plugin_config WHERE plugin_id = #{pluginId} "
            + "AND config_key = #{configKey} AND config_layer = #{configLayer}")
    PluginConfigPO findByKey(@Param("pluginId") String pluginId, @Param("configKey") String configKey,
                             @Param("configLayer") String configLayer);

    @Select("SELECT * FROM harness_plugin_config WHERE plugin_id = #{pluginId} AND config_layer = #{configLayer} "
            + "ORDER BY config_key")
    List<PluginConfigPO> findByPlugin(@Param("pluginId") String pluginId, @Param("configLayer") String configLayer);
}
