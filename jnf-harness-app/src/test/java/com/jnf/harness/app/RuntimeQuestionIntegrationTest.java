package com.jnf.harness.app;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.agent.service.ask.UserQuestionGateway;
import com.jnf.harness.trigger.http.RuntimeQuestionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 问答闭环：挂起 → pending 可见 → REST 作答 → Future 完成 → 挂起清空。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "spring.datasource.url=jdbc:h2:mem:dbq;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class RuntimeQuestionIntegrationTest {

    @Autowired
    UserQuestionGateway.InMemoryUserQuestionGateway gateway;

    @Autowired
    RuntimeQuestionController controller;

    @Test
    @DisplayName("提问挂起 → pending → 作答 → Future 完成；过期问题作答报业务错")
    void question_roundtrip() throws Exception {
        var future = gateway.ask(new UserQuestionGateway.Question(
                "q-1", "想做什么？", List.of("文件操作", "代码编写")));

        List<Map<String, Object>> pending = controller.pending().data();
        assertTrue(pending.stream().anyMatch(item -> "q-1".equals(item.get("questionId"))));

        Response<?> answered = controller.answer("q-1", Map.of("answer", "代码编写"));
        assertEquals("00000", answered.code());
        assertEquals("代码编写", future.get(2, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(controller.pending().data().isEmpty());

        Response<?> stale = controller.answer("q-1", Map.of("answer", "again"));
        assertEquals("40000", stale.code());
    }
}
