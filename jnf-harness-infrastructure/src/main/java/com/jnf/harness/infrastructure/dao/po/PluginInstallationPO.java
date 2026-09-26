package com.jnf.harness.infrastructure.dao.po;

/**
 * 插件安装记录持久化对象。
 */
public class PluginInstallationPO {

    private String pluginId;
    private String displayName;
    private String pluginVersion;
    private String runtimeType;
    private String sourcePath;
    private String entrypoint;
    private String status;
    private java.sql.Timestamp installedAt;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public String getRuntimeType() {
        return runtimeType;
    }

    public void setRuntimeType(String runtimeType) {
        this.runtimeType = runtimeType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.sql.Timestamp getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(java.sql.Timestamp installedAt) {
        this.installedAt = installedAt;
    }
}
