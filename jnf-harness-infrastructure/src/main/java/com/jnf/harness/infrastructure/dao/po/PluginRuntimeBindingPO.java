package com.jnf.harness.infrastructure.dao.po;

/**
 * 插件运行绑定持久化对象（harness_plugin_runtime_binding）。
 */
public class PluginRuntimeBindingPO {

    private String pluginId;
    private String packageType;
    private String manifestPath;
    private String workingDirectory;
    private String runtimeCommand;
    private boolean bridgeReady;
    private String message;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    public void setManifestPath(String manifestPath) {
        this.manifestPath = manifestPath;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getRuntimeCommand() {
        return runtimeCommand;
    }

    public void setRuntimeCommand(String runtimeCommand) {
        this.runtimeCommand = runtimeCommand;
    }

    public boolean isBridgeReady() {
        return bridgeReady;
    }

    public void setBridgeReady(boolean bridgeReady) {
        this.bridgeReady = bridgeReady;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
