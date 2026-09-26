package com.jnf.harness.domain.plugin;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.PluginConfigStore;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginEvent;
import com.jnf.harness.spi.PluginEventBus;
import com.jnf.harness.spi.PluginEventHandler;
import com.jnf.harness.spi.PluginHook;
import com.jnf.harness.domain.tool.service.ToolHookRegistry;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 宿主插件上下文：插件贡献项的事务边界。
 * <p>所有注册项进 registrations；{@link #close()} 逆序回收
 * （插件停用/卸载/启动失败回滚时调用）——对齐参照实现的 SpringPluginContext 语义。
 */
public class HostPluginContext implements PluginContext, AutoCloseable {

    private final String pluginId;
    private final ToolRegistry toolRegistry;
    private final PluginEventBus eventBus;
    private final ToolHookRegistry hookRegistry;
    private final PluginConfigStore configStore;
    private final SystemPromptAssembler promptAssembler;
    private final List<AutoCloseable> registrations = new ArrayList<>();

    public HostPluginContext(String pluginId, ToolRegistry toolRegistry, PluginEventBus eventBus,
                             ToolHookRegistry hookRegistry, PluginConfigStore configStore,
                             SystemPromptAssembler promptAssembler) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.hookRegistry = Objects.requireNonNull(hookRegistry, "hookRegistry");
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.promptAssembler = Objects.requireNonNull(promptAssembler, "promptAssembler");
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public AutoCloseable registerTool(ToolDefinition tool) {
        // 限定名注册：plugin__<pluginId>__<toolName>
        Runnable disposer = toolRegistry.register(new QualifiedTool(pluginId, tool));
        AutoCloseable registration = disposer::run;
        registrations.add(registration);
        return registration;
    }

    @Override
    public void registerDisposer(AutoCloseable disposer) {
        registrations.add(Objects.requireNonNull(disposer, "disposer"));
    }

    @Override
    public AutoCloseable subscribe(String eventName, PluginEventHandler handler) {
        AutoCloseable disposer = eventBus.subscribe(eventName, pluginId, handler);
        registrations.add(disposer);
        return disposer;
    }

    @Override
    public void emit(String eventName, Object payload) {
        eventBus.publish(new PluginEvent(pluginId, eventName, payload, null));
    }

    @Override
    public AutoCloseable registerHook(String hookPoint, PluginHook hook) {
        AutoCloseable disposer = hookRegistry.register(hookPoint, hook);
        registrations.add(disposer);
        return disposer;
    }

    @Override
    public Optional<String> getConfig(String key) {
        return configStore.get(pluginId, key);
    }

    @Override
    public AutoCloseable registerSystemPrompt(String name, int order, String text) {
        String qualifiedName = "plugin__" + pluginId + "__" + name;
        promptAssembler.registerSection(new SystemPromptAssembler.PromptSection(qualifiedName, order, text));
        AutoCloseable disposer = () -> promptAssembler.removeSection(qualifiedName);
        registrations.add(disposer);
        return disposer;
    }

    /** 逆序回收全部贡献项（单项失败不阻断其余；首个异常上抛）。 */
    @Override
    public synchronized void close() throws Exception {
        List<AutoCloseable> ordered = new ArrayList<>(registrations);
        registrations.clear();
        Exception firstFailure = null;
        for (int index = ordered.size() - 1; index >= 0; index--) {
            try {
                ordered.get(index).close();
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
