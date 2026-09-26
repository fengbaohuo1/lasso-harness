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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一阶段全旅程 E2E（能力映射验收的总闸）：
 * 流式对话 → LLM 发起工具调用 → 运行期审批挂起 → REST 放行 → 工具真实执行 → 续步收口 → 历史回放。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:e2etest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class PhaseOneEndToEndTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("全旅程：流式对话 → 工具调用 → 审批放行 → 真实执行 → 完成 → 回放")
    void full_journey_with_approval() throws Exception {
        // 脚本一：发起 fs_write（风险工具，需审批）；脚本二：收口文本
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.ToolCallDelta("c1", "fs_write",
                        "{\"path\":\"e2e-artifact.txt\",\"content\":\"阶段一产物\"}"),
                new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero())));
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(List.of(
                new StreamChunk.TextDelta("写文件完成"),
                new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero())));

        // 1. 发起流式对话（后台线程消费）
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest streamRequest = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/agent/stream"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"agentId\":\"e2e-agent\",\"message\":\"写个文件\"}"))
                .build();
        java.util.concurrent.CompletableFuture<HttpResponse<String>> streamFuture =
                client.sendAsync(streamRequest, HttpResponse.BodyHandlers.ofString());

        // 2. 等待审批挂起 → REST 裁决放行
        String approvalId = null;
        for (int i = 0; i < 150 && approvalId == null; i++) {
            String pending = rest.getForObject(
                    "http://127.0.0.1:" + port + "/api/harness/approvals/runtime/pending", String.class);
            int idIndex = pending.indexOf("\"id\":\"");
            if (idIndex >= 0) {
                approvalId = pending.substring(idIndex + 6, pending.indexOf('"', idIndex + 6));
            } else {
                Thread.sleep(20);
            }
        }
        assertTrue(approvalId != null, "fs_write 应触发运行期审批挂起（R1）");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.postForEntity("http://127.0.0.1:" + port + "/api/harness/approvals/runtime/" + approvalId + "/resolve",
                new HttpEntity<>("{\"verdict\":\"ALLOW_ONCE\"}", headers), String.class);

        // 3. 流收口 + 事件序列与真实产物
        String body = streamFuture.get().body();
        assertTrue(body.contains("event:step_break"), body);
        assertTrue(body.contains("event:tool_result"), body);
        assertTrue(body.contains("event:done"), body);
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("e2e-artifact.txt")),
                "工具必须真实执行（文件落盘）");
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("e2e-artifact.txt"));

        // 4. 历史回放：事件重建可见全过程
        String messages = rest.getForObject("http://127.0.0.1:" + port
                + "/api/harness/console/sessions/e2e-agent/messages", String.class);
        assertTrue(messages.contains("写个文件"), messages);
        assertTrue(messages.contains("写文件完成"), messages);
    }
}
