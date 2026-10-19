package com.jnf.harness.plugin.essentials;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.host.SessionSearchService;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方 essentials 工具插件（A3 内核瘦身）：todo_write / exit_plan_mode / lsp_query / session_search。
 * <p>todo/plan 服务随迁入插件（自足内存态）；session_search 检索经宿主服务
 * {@code lookupService(SessionSearchService.class)}；lsp 为存根。
 */
public class EssentialsPlugin extends AbstractHarnessPlugin {

    private final List<ToolDefinition> tools = new ArrayList<>();

    public EssentialsPlugin() {
        super("essentials");
    }

    @Override
    public List<ToolDefinition> tools() {
        return tools;
    }

    @Override
    public void configure(PluginContext context) {
        SessionSearchService searchService =
                context.lookupService(SessionSearchService.class).orElse(null);
        tools.clear();
        tools.add(new TodoWriteTool(new TodoService()));
        tools.add(new ExitPlanModeTool(new PlanModeService()));
        tools.add(new LspTool());
        tools.add(new SessionSearchTool(searchService));
        super.configure(context);
    }
}
