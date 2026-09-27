package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.channel.ChannelPreset;
import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.domain.runtime.setting.ModelSettingService;
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
    private final ModelSettingService modelSettings;

    public HarnessChannelController(IModelDiscoveryPort discovery, ModelSettingService modelSettings) {
        this.discovery = discovery;
        this.modelSettings = modelSettings;
    }

    /** 内置渠道模板（前端快捷填充，不落库）。 */
    @GetMapping("/channels/presets")
    public Response<List<ChannelPreset>> presets() {
        return Response.success(ChannelPreset.presets());
    }

    /**
     * 探测上游可用模型列表。
     * <p>入参：channelCode（引用既有渠道）或 baseUrl + apiKey（或 apiKeyRef 引用）/protocol。
     */
    @PostMapping("/settings/models/discover")
    public Response<List<String>> discover(@RequestBody Map<String, Object> body) {
        String baseUrl;
        String apiKey;
        String protocol;
        String channelCode = body.get("channelCode") == null ? "" : String.valueOf(body.get("channelCode"));
        if (!channelCode.isBlank()) {
            ModelSettingEntity entity = modelSettings.list().stream()
                    .filter(item -> item.channelCode().equals(channelCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("渠道不存在: " + channelCode));
            baseUrl = entity.baseUrl();
            apiKey = modelSettings.resolveApiKey(channelCode);
            protocol = entity.protocol();
        } else {
            Object rawBaseUrl = body.get("baseUrl");
            if (rawBaseUrl == null || String.valueOf(rawBaseUrl).isBlank()) {
                return Response.invalidArgument("baseUrl 必填");
            }
            baseUrl = String.valueOf(rawBaseUrl);
            protocol = String.valueOf(body.getOrDefault("protocol", "openai"));
            apiKey = body.get("apiKey") == null ? "" : String.valueOf(body.get("apiKey"));
            if (apiKey.isBlank()) {
                Object ref = body.get("apiKeyRef");
                apiKey = modelSettings.secrets().resolve(ref == null ? "" : String.valueOf(ref));
            }
        }
        try {
            return Response.success(discovery.discover(baseUrl, apiKey, protocol));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        } catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }
}
