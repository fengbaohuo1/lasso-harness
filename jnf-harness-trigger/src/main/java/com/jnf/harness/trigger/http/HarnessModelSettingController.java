package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.domain.runtime.setting.ModelSettingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型渠道配置（读写合一）。
 * <p>M1：apiKey 只进不出（写入密钥库，响应仅 hasApiKey）；M3：测试连接。
 */
@RestController
@RequestMapping("/api/harness/settings/models")
public class HarnessModelSettingController {

    private final ModelSettingService service;
    private final IModelDiscoveryPort discovery;

    public HarnessModelSettingController(ModelSettingService service, IModelDiscoveryPort discovery) {
        this.service = service;
        this.discovery = discovery;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ModelSettingEntity entity : service.list()) {
            items.add(toMap(entity));
        }
        return Response.success(items);
    }

    @PostMapping
    public Response<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        ModelSettingEntity entity = new ModelSettingEntity(
                str(body, "channelCode"), str(body, "displayName"), str(body, "providerCode"),
                str(body, "modelCode"), str(body, "baseUrl"), str(body, "apiKeyRef"),
                str(body, "protocol"), bool(body, "enabled", true), false);
        return Response.success(toMap(service.save(entity, str(body, "apiKey"))));
    }

    @DeleteMapping("/{channelCode}")
    public Response<Void> delete(@PathVariable String channelCode) {
        service.delete(channelCode);
        return Response.success(null);
    }

    @PostMapping("/active")
    public Response<Void> activate(@RequestBody Map<String, Object> body) {
        service.activate(str(body, "channelCode"));
        return Response.success(null);
    }

    /**
     * 测试连接（M3）：可达性 + 鉴权探测，返回 {ok, latencyMs, message, models?}。
     * <p>入参 channelCode（引用既有渠道）或内联 baseUrl/apiKey(或 apiKeyRef)/protocol。
     */
    @PostMapping("/test")
    public Response<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        String baseUrl;
        String apiKey;
        String protocol;
        String channelCode = str(body, "channelCode");
        if (!channelCode.isBlank()) {
            ModelSettingEntity entity = service.list().stream()
                    .filter(item -> item.channelCode().equals(channelCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("渠道不存在: " + channelCode));
            baseUrl = entity.baseUrl();
            apiKey = service.resolveApiKey(channelCode);
            protocol = entity.protocol();
        } else {
            baseUrl = str(body, "baseUrl");
            if (baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl 或 channelCode 必填");
            }
            protocol = str(body, "protocol");
            apiKey = str(body, "apiKey");
            if (apiKey.isBlank()) {
                apiKey = service.secrets().resolve(str(body, "apiKeyRef"));
            }
        }
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<String> models = discovery.discover(baseUrl, apiKey,
                    protocol.isBlank() ? "openai" : protocol);
            result.put("ok", true);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", "连接成功");
            result.put("models", models);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", e.getMessage());
        }
        return Response.success(result);
    }

    private Map<String, Object> toMap(ModelSettingEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channelCode", entity.channelCode());
        item.put("displayName", entity.displayName());
        item.put("providerCode", entity.providerCode());
        item.put("modelCode", entity.modelCode());
        item.put("baseUrl", entity.baseUrl());
        item.put("apiKeyRef", entity.apiKeyRef());
        item.put("protocol", entity.protocol());
        item.put("enabled", entity.enabled());
        item.put("active", entity.active());
        item.put("hasApiKey", service.hasApiKey(entity.channelCode()));
        return item;
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> body, String key, boolean defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }
}
