package com.jnf.harness.cases.agent.node;

import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.cases.agent.AgentIntent;
import com.jnf.harness.cases.orchestration.AbstractStrategyRouter;
import com.jnf.harness.cases.orchestration.DynamicContext;

import java.util.regex.Pattern;

/**
 * 节点二：意图分类（纯正则规则，无额外模型调用）。
 * <p>规则顺序：任务执行 > 代码问答 > 澄清 > 闲聊。
 */
public class AgentIntentNode extends AbstractStrategyRouter {

    private static final Pattern TASK = Pattern.compile(
            "执行|运行|部署|安装|删除|创建|写入|生成|修复|重构|帮我|批量|提交|构建|改一下");
    private static final Pattern CODE = Pattern.compile(
            "代码|函数|方法|报错|异常|bug|接口|类|怎么写|为什么|原理|error|stack|compile|编译|日志|报错");

    private final AbstractStrategyRouter next;

    public AgentIntentNode(AbstractStrategyRouter next) {
        this.next = next;
    }

    @Override
    protected void doApply(DynamicContext context) {
        AgentMessageCommand command = context.get("command", AgentMessageCommand.class);
        context.put("intent", classify(command.message()));
    }

    @Override
    protected AbstractStrategyRouter getNext() {
        return next;
    }

    public static AgentIntent classify(String message) {
        String text = message == null ? "" : message.trim();
        if (TASK.matcher(text).find()) {
            return AgentIntent.TASK_EXECUTION;
        }
        if (CODE.matcher(text).find()) {
            return AgentIntent.CODE_QUESTION;
        }
        boolean shortQuestion = text.length() <= 30
                && (text.endsWith("？") || text.endsWith("?") || text.startsWith("请问"));
        if (shortQuestion) {
            return AgentIntent.CLARIFICATION;
        }
        return AgentIntent.CHAT;
    }
}
