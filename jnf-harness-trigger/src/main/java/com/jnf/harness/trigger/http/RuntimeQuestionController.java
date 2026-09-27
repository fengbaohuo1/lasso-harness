package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.agent.service.ask.UserQuestionGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期用户提问（挂起-作答轮询面）：ask_user_question 工具经网关挂起，
 * 前端轮询 pending 展示问题卡，POST answer 完成 Future、流式恢复。
 */
@RestController
@RequestMapping("/api/harness/questions/runtime")
public class RuntimeQuestionController {

    private final UserQuestionGateway.InMemoryUserQuestionGateway gateway;

    public RuntimeQuestionController(UserQuestionGateway.InMemoryUserQuestionGateway gateway) {
        this.gateway = gateway;
    }

    /** 待作答问题列表。 */
    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending() {
        List<Map<String, Object>> items = gateway.pendingQuestions().stream().map(question -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", question.questionId());
            item.put("question", question.question());
            item.put("options", question.options());
            return item;
        }).toList();
        return Response.success(items);
    }

    /** 提交回答：{"answer": "文本"}；完成挂起的 Future，Agent 流式继续。 */
    @PostMapping("/{questionId}/answer")
    public Response<?> answer(@PathVariable String questionId, @RequestBody Map<String, Object> body) {
        boolean accepted = gateway.answer(questionId,
                String.valueOf(body.getOrDefault("answer", "")));
        if (!accepted) {
            return Response.invalidArgument("问题不存在或已过期: " + questionId);
        }
        return Response.success(Map.of("questionId", questionId, "answered", true));
    }
}
