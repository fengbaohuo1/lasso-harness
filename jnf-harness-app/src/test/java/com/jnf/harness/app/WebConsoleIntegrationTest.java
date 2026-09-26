package com.jnf.harness.app;

import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web 控制台验收（第 11 节 DoD）：静态资源 + 消息回放接口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:consoletest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class WebConsoleIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("控制台页面可访问（无构建链静态资源）")
    void console_page_served() {
        ResponseEntity<String> page = rest.getForEntity("http://127.0.0.1:" + port + "/index.html", String.class);
        assertEquals(200, page.getStatusCode().value());
        assertTrue(page.getBody().contains("jnf-harness"), "应返回控制台页面");

        ResponseEntity<String> appJs = rest.getForEntity("http://127.0.0.1:" + port + "/app.js", String.class);
        assertEquals(200, appJs.getStatusCode().value());
        assertTrue(appJs.getBody().contains("api/agent/stream"), "app.js 应含流式调用");
    }

    @Test
    @DisplayName("消息回放：对话后可从事件重建消息")
    void replay_messages() {
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.TextDelta("控制台回放内容"),
                new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero())));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange("http://127.0.0.1:" + port + "/api/agent/message",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"agentId\":\"console-session\",\"message\":\"回放测试\"}", headers),
                String.class);

        ResponseEntity<String> messages = rest.getForEntity(
                "http://127.0.0.1:" + port + "/api/harness/console/sessions/console-session/messages",
                String.class);
        assertEquals(200, messages.getStatusCode().value());
        assertTrue(messages.getBody().contains("回放测试"), messages.getBody());
        assertTrue(messages.getBody().contains("控制台回放内容"), messages.getBody());
    }
}
