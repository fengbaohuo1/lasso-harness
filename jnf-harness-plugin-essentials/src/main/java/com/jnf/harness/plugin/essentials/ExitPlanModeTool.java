package com.jnf.harness.plugin.essentials;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * exit_plan_mode 工具：提交计划并退出计划模式。
 */
public class ExitPlanModeTool extends AbstractTool {

    private final PlanModeService planModeService;

    public ExitPlanModeTool(PlanModeService planModeService) {
        this.planModeService = planModeService;
    }

    @Override
    public String name() {
        return "exit_plan_mode";
    }

    @Override
    public String description() {
        return "提交实施计划并退出计划模式（plan 为计划正文）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema().prop("plan", stringSchema("实施计划")).required("plan").build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        planModeService.enter(ctx.sessionId(), str(args, "plan"));
        PlanModeService.PlanState state = planModeService.exit(ctx.sessionId());
        return ok("计划已提交，退出计划模式（sessionId=" + state.sessionId() + "）");
    }
}
