package com.jnf.harness.plugin.subagent;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.host.AgentRunService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * delegate_task（P8-3）：把独立子任务派发给子智能体执行，返回其最终汇报。
 * <p>适用：可独立拆解的子任务（调研/验证/生成草稿）；不适用：与当前上下文强耦合的碎操作。
 * 子智能体在独立会话（sub-&lt;uuid&gt;，事件可回放）里用自己的预算跑一轮；
 * 递归深度（≤2）与超时（默认 120s）由宿主 {@link AgentRunService} 治理。
 */
public class DelegateTaskTool extends AbstractTool {

    private final AgentRunService agents;

    public DelegateTaskTool(AgentRunService agents) {
        this.agents = agents;
    }

    @Override
    public String name() {
        return "delegate_task";
    }

    @Override
    public String description() {
        return "把一个独立子任务派发给子智能体执行（独立会话/独立预算），返回其最终汇报。"
                + "用于可独立拆解的子任务（调研/验证/草稿），不要用于与当前上下文强耦合的碎操作。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("task", stringSchema("子任务描述（自足可执行，含验收要点）"))
                .prop("context", stringSchema("补充上下文（可选）：子智能体需要的背景/约束"))
                .prop("profile", stringSchema("画像码（可选）：如 faq-tools；缺省 default"))
                .prop("timeoutSeconds", stringSchema("超时秒数（可选，默认 120）"))
                .required("task")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String task = str(args, "task");
        if (task.isBlank()) {
            return fail("task 不能为空", "MISSING_TASK");
        }
        if (agents == null) {
            return fail("宿主未提供 AgentRunService（插件降级）", "NO_HOST_SERVICE");
        }
        int timeout = 120;
        String timeoutRaw = str(args, "timeoutSeconds");
        if (!timeoutRaw.isBlank()) {
            try {
                timeout = Integer.parseInt(timeoutRaw.trim());
            } catch (NumberFormatException ignored) {
                // 非数字沿用默认
            }
        }
        AgentRunService.SubagentRequest request = new AgentRunService.SubagentRequest(
                ctx.sessionId(), task, str(args, "context"), str(args, "profile"), timeout);
        AgentRunService.SubagentOutcome outcome = agents.run(request);
        if (!outcome.ok()) {
            return fail(outcome.error() == null ? "子智能体执行失败" : outcome.error(), "SUBAGENT_FAILED");
        }
        return ok(json(outcome));
    }

    /** 结果 JSON（结构供 UI 渲染器消费）。 */
    private static String json(AgentRunService.SubagentOutcome outcome) {
        return "{\"sessionId\":" + quote(outcome.sessionId())
                + ",\"turn\":" + outcome.turn()
                + ",\"text\":" + quote(outcome.text()) + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t") + '"';
    }
}
