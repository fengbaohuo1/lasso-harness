package com.jnf.harness.trigger.http;

import com.jnf.harness.api.IAgentApi;
import com.jnf.harness.api.dto.AgentMessageCommand;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.attachment.IAttachmentStore;
import com.jnf.harness.domain.runtime.setting.ModelEntity;
import com.jnf.harness.domain.runtime.setting.ModelService;
import com.jnf.harness.model.ImageAttachmentRef;
import com.jnf.harness.trigger.service.stream.AgentStreamApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 对话接入（协议形状对齐参照实现）。
 * <p>P11-4：{@code attachments} 支持 {@code {mediaType, data}}（data 为裸 base64 或 data URI），
 * 在接入层落附件库后只把引用传进领域，并在消息投递前做「模型是否支持图片输入」的能力闸门。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IAgentApi agentApi;
    private final AgentStreamApi streamApi;
    private final IAttachmentStore attachments;
    private final ModelService modelService;

    public AgentController(IAgentApi agentApi, AgentStreamApi streamApi,
                           IAttachmentStore attachments, ModelService modelService) {
        this.agentApi = agentApi;
        this.streamApi = streamApi;
        this.attachments = attachments;
        this.modelService = modelService;
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
        try {
            return streamApi.stream(toCommand(body));
        } catch (IllegalArgumentException e) {
            return streamApi.fail(e.getMessage());
        }
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
        List<ImageAttachmentRef> refs = storeAttachments(body.get("attachments"));
        if (!refs.isEmpty()) {
            requireImageInput(body);
        }
        return new AgentMessageCommand(
                str(body, "agentId"),
                str(body, "message"),
                str(body, "model"),
                body.get("maxTokens") == null ? null : Integer.valueOf(String.valueOf(body.get("maxTokens"))),
                body.get("temperature") == null ? null : Double.valueOf(String.valueOf(body.get("temperature"))),
                str(body, "cwd"),
                str(body, "profile"),
                str(body, "thinking"),
                refs);
    }

    /** 能力闸门：选中的模型已知且不支持图片输入时直接拒绝，避免上游报难懂的协议错误。 */
    private void requireImageInput(Map<String, Object> body) {
        Optional<ModelEntity> spec = modelService.resolve(str(body, "model"));
        if (spec.isPresent() && !spec.get().supportsImageInput()) {
            throw new IllegalArgumentException("当前模型不支持图片输入: " + spec.get().modelCode()
                    + "（可在「模型渠道」里为该模型勾选图片输入）");
        }
    }

    /** 把请求里的图片落附件库，返回可写入事件的引用列表。 */
    private List<ImageAttachmentRef> storeAttachments(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<ImageAttachmentRef> refs = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> item)) {
                continue;
            }
            String data = item.get("data") == null ? "" : String.valueOf(item.get("data")).trim();
            if (data.isEmpty()) {
                continue;
            }
            String mediaType = item.get("mediaType") == null ? "" : String.valueOf(item.get("mediaType")).trim();
            int comma = data.indexOf(',');
            if (data.startsWith("data:") && comma > 0) {
                // 前端 FileReader 给的就是 data URI：媒体类型与 payload 都从里面取
                String header = data.substring(5, comma);
                if (mediaType.isEmpty()) {
                    mediaType = header.split(";")[0];
                }
                data = data.substring(comma + 1);
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(data);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("附件不是合法的 base64 数据");
            }
            String dataRef = attachments.store(mediaType, bytes);
            refs.add(new ImageAttachmentRef(mediaType, dataRef));
        }
        return refs;
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
