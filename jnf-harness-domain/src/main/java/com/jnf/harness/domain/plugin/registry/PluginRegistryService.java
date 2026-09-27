package com.jnf.harness.domain.plugin.registry;

import com.jnf.harness.domain.plugin.PluginHost;
import com.jnf.harness.spi.PluginLifecycleState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 插件注册服务：install（登记）→ activate（加载运行）→ disable/uninstall。
 * <p>状态列与运行态对齐：REGISTERED / ACTIVE / FAILED / DISABLED。
 */
public class PluginRegistryService {

    public static final String RUNTIME_JAVA_NATIVE = "JAVA_NATIVE";
    public static final String RUNTIME_NODE_BRIDGE = "DSH_NODE_BRIDGE";

    private final IPluginInstallationRepository repository;
    private final PluginHost pluginHost;
    private final com.jnf.harness.domain.plugin.INodePluginLoaderPort nodeLoader;
    private final IPluginRuntimeBindingRepository runtimeBinding;

    public PluginRegistryService(IPluginInstallationRepository repository, PluginHost pluginHost,
                                 com.jnf.harness.domain.plugin.INodePluginLoaderPort nodeLoader,
                                 IPluginRuntimeBindingRepository runtimeBinding) {
        this.repository = repository;
        this.pluginHost = pluginHost;
        this.nodeLoader = nodeLoader;
        this.runtimeBinding = runtimeBinding;
    }

    /** 登记插件（制品必须已存在）。 */
    public PluginInstallationEntity install(PluginInstallationEntity entity) {
        if (entity.pluginId() == null || entity.pluginId().isBlank()) {
            throw new IllegalArgumentException("pluginId 必填");
        }
        if (entity.sourcePath() == null || !Files.exists(Path.of(entity.sourcePath()))) {
            throw new IllegalArgumentException("制品不存在: " + entity.sourcePath());
        }
        PluginInstallationEntity record = new PluginInstallationEntity(
                entity.pluginId().trim(), entity.displayName(), entity.pluginVersion(),
                entity.runtimeType() == null ? RUNTIME_JAVA_NATIVE : entity.runtimeType(),
                entity.sourcePath(), entity.entrypoint(), PluginLifecycleState.REGISTERED.name());
        repository.upsert(record);
        return record;
    }

    /** 激活（加载 + 注册工具/提示词/Hook）；按 runtimeType 走 Java / Node 通道。 */
    public void activate(String pluginId) {
        PluginInstallationEntity record = require(pluginId);
        boolean nodeBridge = RUNTIME_NODE_BRIDGE.equalsIgnoreCase(record.runtimeType());
        try {
            if (nodeBridge) {
                if (nodeLoader == null) {
                    throw new IllegalStateException("Node Bridge 未启用");
                }
                pluginHost.activateLoaded(nodeLoader.load(
                        Path.of(record.sourcePath()), record.entrypoint(), pluginId));
            } else {
                pluginHost.activate(Path.of(record.sourcePath()));
            }
        } catch (RuntimeException e) {
            if (nodeBridge) {
                recordBinding(record, false, String.valueOf(e.getMessage()));
            }
            repository.updateStatus(pluginId, PluginLifecycleState.FAILED.name());
            throw e;
        }
        if (nodeBridge) {
            recordBinding(record, true, "bridge ready");
        }
        repository.updateStatus(pluginId, PluginLifecycleState.ACTIVE.name());
    }

    /** P3：Node Bridge 激活状态落 harness_plugin_runtime_binding。 */
    private void recordBinding(PluginInstallationEntity record, boolean ready, String message) {
        if (runtimeBinding == null) {
            return;
        }
        runtimeBinding.upsert(new PluginRuntimeBindingEntity(
                record.pluginId(), record.runtimeType(), record.sourcePath(), record.sourcePath(),
                "node " + (record.entrypoint() == null ? "" : record.entrypoint()), ready, message));
    }

    /** 停用（逆序回收贡献项）。 */
    public void disable(String pluginId) {
        pluginHost.disable(pluginId);
        repository.updateStatus(pluginId, PluginLifecycleState.DISABLED.name());
    }

    /** 热重载：停用（逆序回收）后按记录重新激活——替换制品后调用。 */
    public synchronized void reload(String pluginId) {
        require(pluginId);
        disable(pluginId);
        activate(pluginId);
    }

    /** 卸载：停用 + 删记录。 */
    public void uninstall(String pluginId) {
        pluginHost.disable(pluginId);
        repository.delete(pluginId);
    }

    public List<PluginInstallationEntity> list() {
        return repository.findAll();
    }

    public Optional<PluginInstallationEntity> get(String pluginId) {
        return repository.findById(pluginId);
    }

    /** 实际运行状态（以宿主为准）。 */
    public PluginLifecycleState runtimeState(String pluginId) {
        return pluginHost.stateOf(pluginId);
    }

    private PluginInstallationEntity require(String pluginId) {
        return repository.findById(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("插件未安装: " + pluginId));
    }
}
