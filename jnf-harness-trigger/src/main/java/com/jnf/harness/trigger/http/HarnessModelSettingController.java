package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;
import com.jnf.harness.domain.runtime.setting.ModelEntity;
import com.jnf.harness.domain.runtime.setting.ModelService;
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
 * <p>P11-1：渠道下可挂多个模型（{@code models} 数组整体同步）；P11-2：{@code /catalog} 供对话框模型选择器；
 * P11-3：apiKey 三分语义（缺省=不改 / 空串=清除 / 非空=覆盖）。
 */
@RestController
@RequestMapping("/api/harness/settings/models")
public class HarnessModelSettingController {

    private final ModelSettingService service;
    private final ModelService models;
    private final IModelDiscoveryPort discovery;

    public HarnessModelSettingController(ModelSettingService service, ModelService models,
                                        IModelDiscoveryPort discovery) {
        this.service = service;
        this.models = models;
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

    /**
     * 对话可用的模型清单（P11-1/P11-7）：扁平返回，前端按 {@code channelCode} 分组渲染选择器。
     * <p>只含启用渠道下的启用模型；{@code ref} 即会话要传的 {@code channelCode::modelCode}。
     */
    @GetMapping("/catalog")
    public Response<List<Map<String, Object>>> catalog() {
        Map<String, ModelSettingEntity> channels = new LinkedHashMap<>();
        for (ModelSettingEntity channel : service.list()) {
            channels.put(channel.channelCode(), channel);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ModelEntity model : models.catalog()) {
            ModelSettingEntity channel = channels.get(model.channelCode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ref", model.ref());
            item.put("channelCode", model.channelCode());
            item.put("channelDisplayName", channel == null || channel.displayName() == null
                    ? model.channelCode() : channel.displayName());
            item.put("providerCode", channel == null ? null : channel.providerCode());
            item.put("modelCode", model.modelCode());
            item.put("displayName", model.displayName());
            item.put("contextWindow", model.effectiveContextWindow());
            item.put("maxOutputTokens", model.effectiveMaxOutputTokens());
            item.put("inputModalities", model.inputModalities());
            item.put("outputModalities", model.outputModalities());
            item.put("isDefault", model.isDefault());
            item.put("channelActive", channel != null && channel.active());
            items.add(item);
        }
        return Response.success(items);
    }

    @PostMapping
    public Response<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        String channelCode = str(body, "channelCode");
        ModelSettingEntity entity = new ModelSettingEntity(
                channelCode, str(body, "displayName"), str(body, "providerCode"),
                str(body, "modelCode"), str(body, "baseUrl"), str(body, "apiKeyRef"),
                str(body, "protocol"), bool(body, "enabled", true), false);
        // P11-3：只有显式提交了非 null 的 apiKey 才动密钥；缺省 = 保持既有密钥。
        String apiKey = null;
        if (body.containsKey("apiKey") && body.get("apiKey") != null) {
            apiKey = String.valueOf(body.get("apiKey"));
        }
        ModelSettingEntity saved = service.save(entity, apiKey);
        if (body.get("models") instanceof List<?> raw) {
            models.sync(saved.channelCode(), parseModels(raw, saved.channelCode()), str(body, "defaultModel"));
        } else if (!saved.modelCode().isBlank()) {
            // 老客户端不带 models：只保证默认模型那一行存在，不动既有模型清单
            models.sync(saved.channelCode(),
                    List.of(ModelEntity.minimal(saved.channelCode(), saved.modelCode())), saved.modelCode());
        }
        return Response.success(toMap(saved));
    }

    @DeleteMapping("/{channelCode}")
    public Response<Void> delete(@PathVariable String channelCode) {
        service.delete(channelCode);
        models.deleteByChannel(channelCode);
        return Response.success(null);
    }

    @PostMapping("/active")
    public Response<Void> activate(@RequestBody Map<String, Object> body) {
        service.activate(str(body, "channelCode"));
        return Response.success(null);
    }

    /**
     * 测试连接（M3）：可达性 + 鉴权探测，返回 {ok, latencyMs, message, models?}。
     * <p>入参 channelCode（引用既有渠道，P11-3 编辑态用已存密钥）或内联 baseUrl/apiKey(或 apiKeyRef)/protocol。
     */
    @PostMapping("/test")
    public Response<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        String baseUrl;
        String apiKey;
        String protocol;
        String providerCode;
        String channelCode = str(body, "channelCode");
        if (!channelCode.isBlank()) {
            ModelSettingEntity entity = service.list().stream()
                    .filter(item -> item.channelCode().equals(channelCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("渠道不存在: " + channelCode));
            baseUrl = entity.baseUrl();
            apiKey = service.resolveApiKey(channelCode);
            protocol = entity.protocol();
            providerCode = entity.providerCode();
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
            providerCode = str(body, "providerCode");
        }
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<String> discovered = discovery.discover(baseUrl, apiKey,
                    protocol.isBlank() ? "openai" : protocol);
            result.put("ok", true);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", "连接成功");
            result.put("models", discovered);
            result.put("modelSpecs", ModelSpecJson.ofAll(providerCode, discovered));
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
        List<Map<String, Object>> channelModels = new ArrayList<>();
        for (ModelEntity model : models.listByChannel(entity.channelCode())) {
            channelModels.add(toModelMap(model));
        }
        item.put("models", channelModels);
        return item;
    }

    private static Map<String, Object> toModelMap(ModelEntity model) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", model.ref());
        item.put("modelCode", model.modelCode());
        item.put("displayName", model.displayName());
        item.put("contextWindow", model.effectiveContextWindow());
        item.put("maxOutputTokens", model.effectiveMaxOutputTokens());
        item.put("inputModalities", model.inputModalities());
        item.put("outputModalities", model.outputModalities());
        item.put("enabled", model.enabled());
        item.put("isDefault", model.isDefault());
        return item;
    }

    /** 解析请求里的模型数组；规格缺省时按官方目录补齐（P11-2），用户已填的值优先。 */
    private static List<ModelEntity> parseModels(List<?> raw, String channelCode) {
        List<ModelEntity> parsed = new ArrayList<>();
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            String modelCode = stringOf(map.get("modelCode"));
            if (modelCode.isBlank()) {
                continue;
            }
            var spec = com.jnf.harness.domain.runtime.setting.ModelSpecCatalog.resolve(
                    stringOf(map.get("providerCode")), modelCode);
            parsed.add(new ModelEntity(channelCode, modelCode,
                    blankToNull(stringOf(map.get("displayName"))),
                    integerOf(map.get("contextWindow"), spec.contextWindow()),
                    integerOf(map.get("maxOutputTokens"), spec.maxOutputTokens()),
                    stringListOf(map.get("inputModalities"), spec.inputModalities()),
                    stringListOf(map.get("outputModalities"), spec.outputModalities()),
                    boolOf(map.get("enabled"), true), false, parsed.size()));
        }
        return parsed;
    }

    private static List<String> stringListOf(Object value, List<String> fallback) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return fallback;
        }
        List<String> items = new ArrayList<>();
        for (Object element : list) {
            String text = stringOf(element);
            if (!text.isBlank()) {
                items.add(text);
            }
        }
        return items.isEmpty() ? fallback : items;
    }

    private static Integer integerOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = (int) Double.parseDouble(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOf(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
