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
    private final Path pluginsDir;

    public PluginRegistryService(IPluginInstallationRepository repository, PluginHost pluginHost,
                                 com.jnf.harness.domain.plugin.INodePluginLoaderPort nodeLoader,
                                 IPluginRuntimeBindingRepository runtimeBinding) {
        this(repository, pluginHost, nodeLoader, runtimeBinding, null);
    }

    public PluginRegistryService(IPluginInstallationRepository repository, PluginHost pluginHost,
                                 com.jnf.harness.domain.plugin.INodePluginLoaderPort nodeLoader,
                                 IPluginRuntimeBindingRepository runtimeBinding,
                                 Path pluginsDir) {
        this.repository = repository;
        this.pluginHost = pluginHost;
        this.nodeLoader = nodeLoader;
        this.runtimeBinding = runtimeBinding;
        this.pluginsDir = pluginsDir;
    }

    /** 登记插件（制品必须已存在）。 */
    public PluginInstallationEntity install(PluginInstallationEntity entity) {
        if (entity.pluginId() == null || entity.pluginId().isBlank()) {
            throw new IllegalArgumentException("pluginId 必填");
        }
        Path artifact = resolveSource(entity.sourcePath());
        if (artifact == null || !Files.exists(artifact)) {
            throw new IllegalArgumentException("制品不存在: " + entity.sourcePath());
        }
        PluginInstallationEntity record = new PluginInstallationEntity(
                entity.pluginId().trim(), entity.displayName(), entity.pluginVersion(),
                entity.runtimeType() == null ? RUNTIME_JAVA_NATIVE : entity.runtimeType(),
                normalizeSource(artifact), entity.entrypoint(), PluginLifecycleState.REGISTERED.name());
        repository.upsert(record);
        return record;
    }

    /**
     * 解析制品路径（P3 sourcePath 相对化）：先按原样解析，落空再按插件目录文件名重解析
     * ——修「绝对路径跨机器失效」。
     */
    public Path resolveSource(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        Path direct = Path.of(sourcePath);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        if (pluginsDir != null) {
            Path byName = pluginsDir.resolve(Path.of(sourcePath).getFileName().toString());
            if (Files.exists(byName)) {
                return byName.toAbsolutePath().normalize();
            }
        }
        return direct.toAbsolutePath().normalize();
    }

    /** 制品在插件目录内 → 存相对文件名（跨机器可迁移）；否则存绝对路径。 */
    private String normalizeSource(Path artifact) {
        if (pluginsDir != null) {
            Path normalizedDir = pluginsDir.toAbsolutePath().normalize();
            Path normalized = artifact.toAbsolutePath().normalize();
            if (normalized.startsWith(normalizedDir)) {
                return normalizedDir.relativize(normalized).toString();
            }
        }
        return artifact.toString();
    }

    /** 激活（加载 + 注册工具/提示词/Hook）；按 runtimeType 走 Java / Node 通道。 */
    public void activate(String pluginId) {
        PluginInstallationEntity record = require(pluginId);
        boolean nodeBridge = RUNTIME_NODE_BRIDGE.equalsIgnoreCase(record.runtimeType());
        Path artifact = resolveSource(record.sourcePath());
        if (artifact == null || !Files.exists(artifact)) {
            repository.updateStatus(pluginId, PluginLifecycleState.FAILED.name());
            throw new IllegalStateException("插件制品不存在: " + record.sourcePath()
                    + "（尝试目录重解析后仍失败）");
        }
        try {
            if (nodeBridge) {
                if (nodeLoader == null) {
                    throw new IllegalStateException("Node Bridge 未启用");
                }
                pluginHost.activateLoaded(nodeLoader.load(
                        artifact, record.entrypoint(), pluginId));
            } else {
                pluginHost.activate(artifact);
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
