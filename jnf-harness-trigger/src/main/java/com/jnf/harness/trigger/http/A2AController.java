package com.jnf.harness.trigger.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnf.harness.api.IAgentApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.dto.AgentMessageResult;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.acp.A2aTaskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A2A JSON-RPC 服务端（单入口 /a2a）：
 * message/send（阻塞返回 Task）、tasks/get、tasks/cancel。
 * <p>message/stream 暂未实现（-32601，挂账）；任务态进程内存（重启即失，与参照取舍一致）。
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.a2a", havingValue = "true", matchIfMissing = true)
public class A2AController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IAgentApi agentApi;
    private final A2aTaskService taskService;

    public A2AController(IAgentApi agentApi, A2aTaskService taskService) {
        this.agentApi = agentApi;
        this.taskService = taskService;
    }

    @PostMapping("/a2a")
    public Map<String, Object> handle(@RequestBody Map<String, Object> body) {
        JsonNode request = MAPPER.valueToTree(body);
        long id = request.path("id").asLong(0);
        String method = request.path("method").asText("");
        JsonNode params = request.path("params");
        try {
            return switch (method) {
                case "message/send" -> result(id, messageSend(params));
                case "tasks/get" -> result(id, tasksGet(params));
                case "tasks/cancel" -> result(id, tasksCancel(params));
                case "message/stream" -> error(id, -32601, "message/stream 暂未实现");
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            return error(id, -32603, String.valueOf(e.getMessage()));
        }
    }

    /** message/send：执行一轮 Agent 对话，返回最终 Task。 */
    private ObjectNode messageSend(JsonNode params) {
        String text = params.path("message").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) {
            throw new IllegalArgumentException("message.parts[0].text 必填");
        }
        String contextId = params.path("contextId").asText("a2a-default");
        A2aTaskService.A2aTask task = taskService.create(contextId);
        taskService.update(task.taskId(), "working", "");

        Response<AgentMessageResult> response = agentApi.send(
                new AgentMessageCommand("a2a-" + contextId, text, null, null, null, null));
        String output = response.ok() && response.data() != null ? response.data().text() : "";
        A2aTaskService.A2aTask finished = taskService.update(task.taskId(),
                response.ok() ? "completed" : "failed", output);
        return taskJson(finished, output);
    }

    private ObjectNode tasksGet(JsonNode params) {
        A2aTaskService.A2aTask task = taskService.get(params.path("id").asText(""));
        return taskJson(task, task.output());
    }

    private ObjectNode tasksCancel(JsonNode params) {
        A2aTaskService.A2aTask task = taskService.get(params.path("id").asText(""));
        if ("completed".equals(task.state()) || "failed".equals(task.state()) || "canceled".equals(task.state())) {
            return errorNode(-32002, "TaskNotCancelable");
        }
        A2aTaskService.A2aTask canceled = taskService.update(task.taskId(), "canceled", task.output());
        return taskJson(canceled, canceled.output());
    }

    private ObjectNode taskJson(A2aTaskService.A2aTask task, String output) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("kind", "task");
        json.put("id", task.taskId());
        json.put("contextId", task.contextId());
        ObjectNode status = json.putObject("status");
        status.put("state", task.state());
        ArrayNode history = json.putArray("history");
        ObjectNode message = history.addObject();
        message.put("role", "agent");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("text", output);
        return json;
    }

    private Map<String, Object> result(long id, ObjectNode resultNode) {
        ObjectNode rpc = MAPPER.createObjectNode();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", id);
        rpc.set("result", resultNode);
        return MAPPER.convertValue(rpc, Map.class);
    }

    private Map<String, Object> error(long id, int code, String message) {
        return errorAsMap(id, code, message);
    }

    private ObjectNode errorNode(int code, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        return node;
    }

    private Map<String, Object> errorAsMap(long id, int code, String message) {
        ObjectNode rpc = MAPPER.createObjectNode();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", id);
        rpc.set("error", errorNode(code, message));
        return MAPPER.convertValue(rpc, Map.class);
    }
}
