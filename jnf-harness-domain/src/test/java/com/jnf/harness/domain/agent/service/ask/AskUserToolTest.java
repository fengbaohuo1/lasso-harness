package com.jnf.harness.domain.agent.service.ask;

import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ask_user_question（M8）：选项分隔兼容全角、作答闭环、超时语义。
 */
class AskUserToolTest {

    private final UserQuestionGateway.InMemoryUserQuestionGateway gateway =
            new UserQuestionGateway.InMemoryUserQuestionGateway();

    private ToolRunContext context() {
        return new ToolRunContext() {
            @Override
            public String agentId() {
                return "a";
            }

            @Override
            public String sessionId() {
                return "s";
            }

            @Override
            public String cwd() {
                return ".";
            }
        };
    }

    @Test
    @DisplayName("选项分隔符兼容中文逗号/顿号/分号")
    void splits_options_with_fullwidth_separators() throws Exception {
        AskUserTool tool = new AskUserTool(gateway, 5);
        var future = tool.execute(Map.of(
                "question", "选哪个？",
                "options", "文件操作，代码编写、信息搜索;其他"), context());

        UserQuestionGateway.Question pending = gateway.pendingQuestions().get(0);
        assertEquals(List.of("文件操作", "代码编写", "信息搜索", "其他"), pending.options());

        assertTrue(gateway.answer(pending.questionId(), "代码编写"));
        ToolExecutionResult result = future.get(2, TimeUnit.SECONDS);
        assertEquals("代码编写", result.content());
    }

    @Test
    @DisplayName("question 为空 → 结构化失败")
    void blank_question_fails() throws Exception {
        AskUserTool tool = new AskUserTool(gateway, 5);
        ToolExecutionResult result = tool.execute(Map.of("question", ""), context())
                .get(2, TimeUnit.SECONDS);
        assertTrue(result.failed());
    }
}
