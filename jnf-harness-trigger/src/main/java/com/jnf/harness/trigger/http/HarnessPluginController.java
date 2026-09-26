package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理（install/activate/disable/uninstall + 查询）。
 */
@RestController
@RequestMapping("/api/harness/plugins")
public class HarnessPluginController {

    private final PluginRegistryService registry;

    public HarnessPluginController(PluginRegistryService registry) {
        this.registry = registry;
    }

    @PostMapping("/install")
    public Response<?> install(@RequestBody Map<String, Object> body) {
        PluginInstallationEntity entity = new PluginInstallationEntity(
                str(body, "pluginId"), str(body, "displayName"), str(body, "pluginVersion"),
                str(body, "runtimeType"), str(body, "sourcePath"), str(body, "entrypoint"), null);
        return Response.success(registry.install(entity));
    }

    @PostMapping("/activate")
    public Response<?> activate(@RequestBody Map<String, Object> body) {
        registry.activate(str(body, "pluginId"));
        return Response.success(Map.of("pluginId", str(body, "pluginId"), "status", "ACTIVE"));
    }

    @PostMapping("/{pluginId}/disable")
    public Response<?> disable(@PathVariable String pluginId) {
        registry.disable(pluginId);
        return Response.success(Map.of("pluginId", pluginId, "status", "DISABLED"));
    }

    @DeleteMapping("/{pluginId}")
    public Response<?> uninstall(@PathVariable String pluginId) {
        registry.uninstall(pluginId);
        return Response.success(null);
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list() {
        List<Map<String, Object>> items = registry.list().stream().map(record -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginId", record.pluginId());
            item.put("displayName", record.displayName());
            item.put("pluginVersion", record.pluginVersion());
            item.put("runtimeType", record.runtimeType());
            item.put("status", record.status());
            item.put("runtimeState", registry.runtimeState(record.pluginId()).name());
            return item;
        }).toList();
        return Response.success(items);
    }

    @GetMapping("/{pluginId}/status")
    public Response<Map<String, Object>> status(@PathVariable String pluginId) {
        return registry.get(pluginId)
                .map(record -> Response.success(Map.<String, Object>of(
                        "pluginId", record.pluginId(),
                        "status", record.status(),
                        "runtimeState", registry.runtimeState(pluginId).name())))
                .orElseGet(() -> Response.invalidArgument("插件未安装: " + pluginId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
