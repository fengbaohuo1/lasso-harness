package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.channel.ChannelPreset;
import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 渠道模板 + 模型发现。
 */
@RestController
@RequestMapping("/api/harness")
public class HarnessChannelController {

    private final IModelDiscoveryPort discovery;

    public HarnessChannelController(IModelDiscoveryPort discovery) {
        this.discovery = discovery;
    }

    /** 内置渠道模板（前端快捷填充，不落库）。 */
    @GetMapping("/channels/presets")
    public Response<List<ChannelPreset>> presets() {
        return Response.success(ChannelPreset.presets());
    }

    /** 探测上游可用模型列表。 */
    @PostMapping("/settings/models/discover")
    public Response<List<String>> discover(@RequestBody Map<String, Object> body) {
        Object baseUrl = body.get("baseUrl");
        if (baseUrl == null || String.valueOf(baseUrl).isBlank()) {
            return Response.invalidArgument("baseUrl 必填");
        }
        try {
            return Response.success(discovery.discover(String.valueOf(baseUrl),
                    String.valueOf(body.getOrDefault("apiKeyRef", "")),
                    String.valueOf(body.getOrDefault("protocol", "openai"))));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        } catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }
}
