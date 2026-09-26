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
 * 开放协议验收（第 13a 节 DoD）：卡片发现 + A2A message/send + 数字人注册表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:prottest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class OpenProtocolIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("三张 Agent Card 可发现（dsh.v1 + A2A 标准 + 兼容路径）")
    void agent_cards_discoverable() {
        String dsh = rest.getForObject(url("/.well-known/dsh-agent-card"), String.class);
        assertTrue(dsh.contains("\"protocol\":\"dsh.v1\""), dsh);

        String a2a = rest.getForObject(url("/.well-known/agent-card.json"), String.class);
        assertTrue(a2a.contains("\"protocolVersion\":\"0.3.0\""), a2a);
        assertTrue(a2a.contains("\"/a2a\""), a2a);

        String legacy = rest.getForObject(url("/.well-known/agent.json"), String.class);
        assertTrue(legacy.contains("protocolVersion"), legacy);
    }

    @Test
    @DisplayName("A2A message/send：发现→执行→Task completed；tasks/get/cancel 语义")
    void a2a_message_send() {
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.TextDelta("A2A 完成"),
                new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero())));

        String sendBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\","
                + "\"params\":{\"contextId\":\"ctx-1\",\"message\":{\"parts\":[{\"text\":\"帮我跑个任务\"}]}}}";
        ResponseEntity<String> send = rest.exchange(url("/a2a"),
                org.springframework.http.HttpMethod.POST, new HttpEntity<>(sendBody, json()), String.class);
        assertTrue(send.getBody().contains("\"kind\":\"task\""), send.getBody());
        assertTrue(send.getBody().contains("\"state\":\"completed\""), send.getBody());
        assertTrue(send.getBody().contains("A2A 完成"), send.getBody());

        // tasks/get 回查
        int idIndex = send.getBody().indexOf("\"id\":\"") + 6;
        String taskId = send.getBody().substring(idIndex, send.getBody().indexOf('"', idIndex));
        ResponseEntity<String> get = rest.exchange(url("/a2a"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/get\",\"params\":{\"id\":\""
                        + taskId + "\"}}", json()), String.class);
        assertTrue(get.getBody().contains("\"state\":\"completed\""), get.getBody());

        // 终态任务不可取消（-32002）
        ResponseEntity<String> cancel = rest.exchange(url("/a2a"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tasks/cancel\",\"params\":{\"id\":\""
                        + taskId + "\"}}", json()), String.class);
        assertTrue(cancel.getBody().contains("-32002"), cancel.getBody());

        // 未知方法 -32601
        ResponseEntity<String> unknown = rest.exchange(url("/a2a"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"nope\"}", json()), String.class);
        assertTrue(unknown.getBody().contains("-32601"), unknown.getBody());
    }

    @Test
    @DisplayName("数字人注册表：创建→挂载端点（自动探测卡片）→删除")
    void digital_human_registry() {
        ResponseEntity<String> created = rest.exchange(url("/api/digital-humans"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"测试数字人\",\"purpose\":\"联调\",\"roleTags\":\"dev\"}", json()),
                String.class);
        assertTrue(created.getBody().contains("测试数字人"), created.getBody());
        int idIndex = created.getBody().indexOf("\"id\":\"") + 6;
        String humanId = created.getBody().substring(idIndex, created.getBody().indexOf('"', idIndex));

        // 挂载端点：baseUrl 指向自己，discover 自动探测本机卡片
        ResponseEntity<String> endpoint = rest.exchange(url("/api/digital-humans/" + humanId + "/endpoints"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"baseUrl\":\"http://127.0.0.1:" + port + "\"}", json()), String.class);
        assertTrue(endpoint.getBody().contains("agentCard"), endpoint.getBody());
        assertTrue(endpoint.getBody().contains("protocolVersion"), endpoint.getBody());

        assertEquals(200, rest.exchange(url("/api/digital-humans/" + humanId),
                org.springframework.http.HttpMethod.DELETE, new HttpEntity<>(json()), String.class)
                .getStatusCode().value());
    }
}
