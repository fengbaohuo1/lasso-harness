package com.jnf.harness.domain.goal;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * goal_update 工具：更新会话续期目标。
 */
public class GoalTool extends AbstractTool {

    private final GoalService goalService;

    public GoalTool(GoalService goalService) {
        this.goalService = goalService;
    }

    @Override
    public String name() {
        return "goal_update";
    }

    @Override
    public String description() {
        return "更新当前会话的续期目标（title/description/status/max_rounds）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("title", stringSchema("目标标题"))
                .prop("description", stringSchema("目标描述"))
                .prop("status", stringSchema("ACTIVE/PAUSED/BLOCKED/COMPLETED/CLEARED"))
                .prop("max_rounds", stringSchema("最大轮次（可选）"))
                .required("title")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        int maxRounds;
        try {
            maxRounds = Integer.parseInt(str(args, "max_rounds").isBlank()
                    ? "0" : str(args, "max_rounds").trim());
        } catch (NumberFormatException e) {
            maxRounds = 0;
        }
        GoalService.GoalSnapshot snapshot = goalService.update(ctx.sessionId(),
                str(args, "title"), str(args, "description"), str(args, "status"), maxRounds);
        return ok("goal=" + snapshot.title() + " status=" + snapshot.status()
                + " revision=" + snapshot.revision());
    }
}
