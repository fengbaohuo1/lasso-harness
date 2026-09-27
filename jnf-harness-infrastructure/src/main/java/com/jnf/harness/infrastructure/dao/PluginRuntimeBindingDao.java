package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.PluginRuntimeBindingPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件运行绑定 DAO（harness_plugin_runtime_binding）。
 */
@Mapper
public interface PluginRuntimeBindingDao {

    @Insert("INSERT INTO harness_plugin_runtime_binding "
            + "(plugin_id, package_type, manifest_path, working_directory, runtime_command, bridge_ready, message, checked_at) "
            + "VALUES (#{pluginId}, #{packageType}, #{manifestPath}, #{workingDirectory}, #{runtimeCommand}, "
            + "#{bridgeReady}, #{message}, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE package_type = VALUES(package_type), manifest_path = VALUES(manifest_path), "
            + "working_directory = VALUES(working_directory), runtime_command = VALUES(runtime_command), "
            + "bridge_ready = VALUES(bridge_ready), message = VALUES(message), checked_at = CURRENT_TIMESTAMP")
    void upsert(PluginRuntimeBindingPO po);
}
