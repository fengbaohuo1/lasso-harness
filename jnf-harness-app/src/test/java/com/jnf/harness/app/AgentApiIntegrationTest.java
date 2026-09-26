package com.jnf.harness.app;

import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.GenerateOptions;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接入层集成验收（第 6 节 DoD）：鉴权 401、阻塞对话、SSE 完整事件序列。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key", "harness.llm.default.base-url=http://127.0.0.1:1/v1",
                "spring.datasource.url=jdbc:h2:mem:apitest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@org.springframework.context.annotation.Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class AgentApiIntegrationTest {

    /** 脚本化假 LLM（@Primary 覆盖真实网关）。 */
    @Configuration
    static class FakeLlmConfiguration {
        static final Deque<List<StreamChunk>> SCRIPTS = new ArrayDeque<>();

        @Bean
        @Primary
        ILlmRuntimePort fakeLlm() {
            return options -> subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                List<StreamChunk> script = SCRIPTS.isEmpty()
                        ? List.of(new StreamChunk.TextDelta("（无脚本）"),
                        new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero()))
                        : SCRIPTS.poll();
                for (StreamChunk chunk : script) {
                    subscriber.onNext(chunk);
                }
                subscriber.onComplete();
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private HttpHeaders headers(boolean withKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (withKey) {
            headers.set("X-API-Key", "test-key");
        }
        return headers;
    }

    @Test
    @DisplayName("无 API Key → 401（鉴权生效）")
    void rejects_without_api_key() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/message"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"agentId\":\"a1\",\"message\":\"hi\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("阻塞对话：envelope code 00000 + 结果文本")
    void message_returns_envelope() {
        FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.TextDelta("你好呀"),
                new StreamChunk.DoneChunk(FinishReason.STOP, new TokenUsage(2, 3))));
        ResponseEntity<String> response = rest.exchange(
                "http://127.0.0.1:" + port + "/api/agent/message", HttpMethod.POST,
                new HttpEntity<>("{\"agentId\":\"a-msg\",\"message\":\"hi\"}", headers(true)), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"00000\""), response.getBody());
        assertTrue(response.getBody().contains("你好呀"), response.getBody());
    }

    @Test
    @DisplayName("参数缺失 → code 40000（R3 统一错误）")
    void invalid_argument_code() {
        ResponseEntity<String> response = rest.exchange(
                "http://127.0.0.1:" + port + "/api/agent/message", HttpMethod.POST,
                new HttpEntity<>("{\"agentId\":\"a2\"}", headers(true)), String.class);
        assertTrue(response.getBody().contains("\"code\":\"40000\""), response.getBody());
    }

    @Test
    @DisplayName("SSE 流式：meta → chunk → finish → done 全序列")
    void stream_full_event_sequence() throws Exception {
        FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.TextDelta("流式"),
                new StreamChunk.TextDelta("输出"),
                new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero())));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/stream"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", "test-key")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"agentId\":\"a-stream\",\"message\":\"hi\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("event:meta"), body);
        assertTrue(body.contains("event:chunk"), body);
        assertTrue(body.contains("event:finish"), body);
        assertTrue(body.contains("event:done"), body);
        assertTrue(body.indexOf("event:meta") < body.indexOf("event:chunk")
                && body.indexOf("event:chunk") < body.indexOf("event:done"), "事件顺序不符");
    }
}
