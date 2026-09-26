package com.jnf.harness.trigger.http;

import com.jnf.harness.api.IAgentApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.trigger.service.stream.AgentStreamApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 对话接入（协议形状对齐参照实现）。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IAgentApi agentApi;
    private final AgentStreamApi streamApi;

    public AgentController(IAgentApi agentApi, AgentStreamApi streamApi) {
        this.agentApi = agentApi;
        this.streamApi = streamApi;
    }

    /** 阻塞式对话。 */
    @PostMapping("/message")
    public Response<?> message(@RequestBody Map<String, Object> body) {
        try {
            return agentApi.send(toCommand(body));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    /** 流式对话（SSE）。 */
    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody Map<String, Object> body) {
        return streamApi.stream(toCommand(body));
    }

    @GetMapping("/{agentId}/status")
    public Response<?> status(@PathVariable String agentId) {
        return agentApi.status(agentId);
    }

    @PostMapping("/{agentId}/cancel")
    public Response<?> cancel(@PathVariable String agentId) {
        return agentApi.cancel(agentId);
    }

    private AgentMessageCommand toCommand(Map<String, Object> body) {
        return new AgentMessageCommand(
                str(body, "agentId"),
                str(body, "message"),
                str(body, "model"),
                body.get("maxTokens") == null ? null : Integer.valueOf(String.valueOf(body.get("maxTokens"))),
                body.get("temperature") == null ? null : Double.valueOf(String.valueOf(body.get("temperature"))),
                str(body, "cwd"));
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
