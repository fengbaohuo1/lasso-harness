package com.jnf.harness.domain.plugin;

import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.tool.service.ToolHookRegistry;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.spi.JavaHarnessPlugin;
import com.jnf.harness.spi.PluginConfigStore;
import com.jnf.harness.spi.PluginEventBus;
import com.jnf.harness.spi.PluginLifecycleState;
import com.jnf.harness.spi.PluginManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件生命周期宿主：install（加载）→ run（configure+onStart）→ disable/uninstall（逆序回收）。
 * <p>启动失败自动回滚到未注册状态（上下文事务边界语义）。
 */
public class PluginHost {

    private static final Logger log = LoggerFactory.getLogger(PluginHost.class);

    private record ActivePlugin(PluginManifest manifest, JavaHarnessPlugin plugin,
                               HostPluginContext context, AutoCloseable resource) {
    }

    private final IPluginLoaderPort loader;
    private final ToolRegistry toolRegistry;
    private final SystemPromptAssembler promptAssembler;
    private final ToolHookRegistry hookRegistry;
    private final PluginEventBus eventBus;
    private final PluginConfigStore configStore;
    private final Map<String, ActivePlugin> active = new ConcurrentHashMap<>();
    private final Map<String, PluginLifecycleState> states = new ConcurrentHashMap<>();

    public PluginHost(IPluginLoaderPort loader, ToolRegistry toolRegistry,
                      SystemPromptAssembler promptAssembler, ToolHookRegistry hookRegistry,
                      PluginEventBus eventBus, PluginConfigStore configStore) {
        this.loader = loader;
        this.toolRegistry = toolRegistry;
        this.promptAssembler = promptAssembler;
        this.hookRegistry = hookRegistry;
        this.eventBus = eventBus;
        this.configStore = configStore;
    }

    /** 激活 Java 插件制品（加载 + configure + onStart）；失败回滚并置 FAILED。 */
    public synchronized void activate(Path artifact) {
        activateLoaded(loader.load(artifact));
    }

    /** 激活已加载的插件（Java/Node/MCP 同构入口）；失败回滚并置 FAILED。 */
    public synchronized void activateLoaded(IPluginLoaderPort.LoadedPlugin loaded) {
        String pluginId = loaded.manifest().id();
        if (active.containsKey(pluginId)) {
            throw new IllegalStateException("插件已激活: " + pluginId);
        }
        HostPluginContext context = new HostPluginContext(pluginId, toolRegistry,
                eventBus, hookRegistry, configStore, promptAssembler);
        try {
            loaded.plugin().configure(context);
            loaded.plugin().onStart();
            active.put(pluginId, new ActivePlugin(loaded.manifest(), loaded.plugin(), context, loaded.resource()));
            states.put(pluginId, PluginLifecycleState.ACTIVE);
            log.info("插件已激活: {} ({})", pluginId, loaded.manifest().version());
        } catch (Exception e) {
            rollback(context, loaded.resource());
            states.put(pluginId, PluginLifecycleState.FAILED);
            throw new IllegalStateException("插件激活失败: " + pluginId + ": " + e.getMessage(), e);
        }
    }

    /** 停用/卸载：onStop + 逆序回收全部贡献项 + 关闭类加载器。 */
    public synchronized void disable(String pluginId) {
        ActivePlugin plugin = active.remove(pluginId);
        if (plugin == null) {
            return;
        }
        try {
            plugin.plugin().onStop();
        } catch (Exception e) {
            log.warn("插件 onStop 异常: {}: {}", pluginId, e.getMessage());
        }
        rollback(plugin.context(), plugin.resource());
        states.put(pluginId, PluginLifecycleState.DISABLED);
        log.info("插件已停用（注册项已逆序回收）: {}", pluginId);
    }

    private void rollback(HostPluginContext context, AutoCloseable resource) {
        try {
            context.close();
        } catch (Exception e) {
            log.warn("插件贡献项回收异常: {}", e.getMessage());
        }
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Exception e) {
            log.warn("插件资源关闭异常: {}", e.getMessage());
        }
    }

    public List<String> activeIds() {
        return new ArrayList<>(active.keySet());
    }

    public PluginLifecycleState stateOf(String pluginId) {
        if (active.containsKey(pluginId)) {
            return PluginLifecycleState.ACTIVE;
        }
        return states.getOrDefault(pluginId, PluginLifecycleState.REGISTERED);
    }
}
