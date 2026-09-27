package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.plugin.registry.IPluginCatalogPort;
import com.jnf.harness.domain.plugin.registry.PluginConfigService;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理（install/activate/disable/reload/uninstall + 目录发现 + 配置 CRUD）。
 */
@RestController
@RequestMapping("/api/harness/plugins")
public class HarnessPluginController {

    private final PluginRegistryService registry;
    private final PluginConfigService configService;
    private final IPluginCatalogPort catalog;
    private final String pluginsDir;

    public HarnessPluginController(PluginRegistryService registry, PluginConfigService configService,
                                   IPluginCatalogPort catalog,
                                   @Value("${harness.plugins.dir:./plugins}") String pluginsDir) {
        this.registry = registry;
        this.configService = configService;
        this.catalog = catalog;
        this.pluginsDir = pluginsDir;
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

    /** P3：热重载（停用逆序回收后按目录重新激活，替换制品后用）。 */
    @PostMapping("/{pluginId}/reload")
    public Response<?> reload(@PathVariable String pluginId) {
        registry.reload(pluginId);
        return Response.success(Map.of("pluginId", pluginId, "status", "ACTIVE"));
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
            item.put("sourcePath", record.sourcePath());
            item.put("entrypoint", record.entrypoint());
            item.put("status", record.status());
            item.put("runtimeState", registry.runtimeState(record.pluginId()).name());
            return item;
        }).toList();
        return Response.success(items);
    }

    /** P2：目录扫描——可安装候选（含 installed 标记）。 */
    @GetMapping("/catalog")
    public Response<List<Map<String, Object>>> catalog() {
        List<String> installedIds = registry.list().stream()
                .map(PluginInstallationEntity::pluginId).toList();
        List<String> installedPaths = registry.list().stream()
                .map(PluginInstallationEntity::sourcePath).toList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (IPluginCatalogPort.CatalogEntry entry : catalog.scan(Path.of(pluginsDir))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginId", entry.pluginId());
            item.put("displayName", entry.displayName());
            item.put("version", entry.version());
            item.put("runtimeType", entry.runtimeType());
            item.put("sourcePath", entry.sourcePath());
            item.put("entrypoint", entry.entrypoint());
            item.put("description", entry.description());
            item.put("installed", installedIds.contains(entry.pluginId())
                    || installedPaths.contains(entry.sourcePath()));
            items.add(item);
        }
        return Response.success(items);
    }

    /** P1：插件配置 KV（layer=default）。 */
    @GetMapping("/{pluginId}/config")
    public Response<Map<String, String>> getConfig(@PathVariable String pluginId) {
        return Response.success(configService.get(pluginId));
    }

    @PutMapping("/{pluginId}/config")
    public Response<Map<String, String>> saveConfig(@PathVariable String pluginId,
                                                   @RequestBody Map<String, String> body) {
        configService.save(pluginId, body);
        return Response.success(configService.get(pluginId));
    }

    @DeleteMapping("/{pluginId}/config/{configKey}")
    public Response<Void> deleteConfig(@PathVariable String pluginId, @PathVariable String configKey) {
        configService.deleteKey(pluginId, configKey);
        return Response.success(null);
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
