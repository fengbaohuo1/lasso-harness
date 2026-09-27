package com.jnf.harness.domain.agent.service.ask;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ask_user_question 工具：向用户发起澄清问题并等待回答（阻塞至超时）。
 */
public class AskUserTool extends AbstractTool {

    private final UserQuestionGateway gateway;
    private final long timeoutSeconds;

    public AskUserTool(UserQuestionGateway gateway, long timeoutSeconds) {
        this.gateway = gateway;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
    }

    @Override
    public String name() {
        return "ask_user_question";
    }

    @Override
    public String description() {
        return "向用户提一个澄清问题并等待回答。在信息不足、方向不明时使用，不要猜。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("question", stringSchema("要问的问题"))
                .prop("options", stringSchema("候选答案（逗号分隔，可选）"))
                .required("question")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String question = str(args, "question");
        if (question.isBlank()) {
            return fail("question 不能为空", "MISSING_QUESTION");
        }
        List<String> options = new ArrayList<>();
        // 选项分隔符兼容半/全角：, ， 、 ; ；
        for (String option : str(args, "options").split("[,，、;；]")) {
            if (!option.isBlank()) {
                options.add(option.trim());
            }
        }
        String questionId = UUID.randomUUID().toString();
        CompletableFuture<String> answer = gateway.ask(
                new UserQuestionGateway.Question(questionId, question, options));
        return answer
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .handle((text, error) -> {
                    if (error != null) {
                        return ToolExecutionResult.fail("等待用户回答超时", "QUESTION_TIMEOUT");
                    }
                    return ToolExecutionResult.ok(text == null ? "" : text);
                });
    }
}
