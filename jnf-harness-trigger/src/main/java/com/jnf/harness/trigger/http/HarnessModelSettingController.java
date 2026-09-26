package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.domain.runtime.setting.ModelSettingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型渠道配置（读写合一；CQRS 拆分见后续整理）。
 */
@RestController
@RequestMapping("/api/harness/settings/models")
public class HarnessModelSettingController {

    private final ModelSettingService service;

    public HarnessModelSettingController(ModelSettingService service) {
        this.service = service;
    }

    @GetMapping
    public Response<List<ModelSettingEntity>> list() {
        return Response.success(service.list());
    }

    @PostMapping
    public Response<ModelSettingEntity> save(@RequestBody Map<String, Object> body) {
        ModelSettingEntity entity = new ModelSettingEntity(
                str(body, "channelCode"), str(body, "displayName"), str(body, "providerCode"),
                str(body, "modelCode"), str(body, "baseUrl"), str(body, "apiKeyRef"),
                str(body, "protocol"), bool(body, "enabled", true), false);
        return Response.success(service.save(entity));
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

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> body, String key, boolean defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }
}
