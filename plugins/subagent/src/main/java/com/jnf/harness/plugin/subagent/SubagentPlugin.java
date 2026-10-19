package com.jnf.harness.plugin.subagent;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.host.AgentRunService;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认子智能体插件（P8-3）：提供 {@code delegate_task}——把独立子任务派发给子智能体执行。
 * <p>执行通道经 {@code lookupService(AgentRunService.class)} 取自宿主：子会话独立、
 * 递归深度与超时由宿主治理（防工具自举爆炸）；宿主未提供服务时工具注册但降级报错。
 * plugin.yaml 声明 {@code toolNamespace: global}。
 */
public class SubagentPlugin extends AbstractHarnessPlugin {

    private final List<ToolDefinition> tools = new ArrayList<>();

    public SubagentPlugin() {
        super("subagent");
    }

    @Override
    public List<ToolDefinition> tools() {
        return tools;
    }

    @Override
    public void configure(PluginContext context) {
        AgentRunService agents = context.lookupService(AgentRunService.class).orElse(null);
        tools.clear();
        tools.add(new DelegateTaskTool(agents));
        super.configure(context);
    }
}
