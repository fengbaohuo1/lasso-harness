package com.jnf.harness.app.boot;

import com.jnf.harness.domain.plugin.IPluginLoaderPort;
import com.jnf.harness.domain.plugin.PluginHost;
import com.jnf.harness.domain.plugin.registry.IPluginCatalogPort;
import com.jnf.harness.domain.plugin.registry.PluginDirectoryResolver;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.infrastructure.adapter.plugin.JavaPluginLoader;
import com.jnf.harness.spi.JavaHarnessPlugin;
import com.jnf.harness.spi.PluginLifecycleState;
import com.jnf.harness.spi.PluginManifest;
import com.jnf.harness.spi.SdkVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * P3 插件启动装配（加载双通道）：
 * <ol>
 *   <li><b>目录自动装载</b>：{@code plugins/} 下 Java 制品（jar + plugin.yaml）自动安装+激活
 *       ——「放进目录重启即用」；用户显式停用（DISABLED）的不复活；Node/MCP 制品仍手动安装；</li>
 *   <li><b>DB 恢复</b>：status=ACTIVE 的安装记录逐一激活（sourcePath 支持相对文件名重解析）；</li>
 *   <li><b>classpath 发现</b>：ServiceLoader 找到的 JavaHarnessPlugin 直接激活
 *       （开发/测试把插件模块放 classpath 即生效；不落安装表）。</li>
 * </ol>
 * 任一失败只记日志，不阻断启动（SD6：内核可空启动）。
 */
public class PluginBootActivator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginBootActivator.class);

    private final PluginRegistryService registry;
    private final PluginHost pluginHost;
    private final IPluginCatalogPort catalog;
    private final String configuredPluginsDir;
    private final boolean autoLoad;

    public PluginBootActivator(PluginRegistryService registry, PluginHost pluginHost,
                               IPluginCatalogPort catalog,
                               String configuredPluginsDir, boolean autoLoad) {
        this.registry = registry;
        this.pluginHost = pluginHost;
        this.catalog = catalog;
        this.configuredPluginsDir = configuredPluginsDir;
        this.autoLoad = autoLoad;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (autoLoad) {
            autoLoadFromDirectory(PluginDirectoryResolver.resolve(configuredPluginsDir));
        }
        restoreActiveRecords();
        activateClasspathPlugins();
    }

    /** 目录自动装载：新 Java 制品 → 登记 + 激活；已登记 ACTIVE → 激活；用户停用的跳过。 */
    private void autoLoadFromDirectory(Path pluginsDir) {
        try {
            for (IPluginCatalogPort.CatalogEntry entry : catalog.scan(pluginsDir)) {
                if (!PluginRegistryService.RUNTIME_JAVA_NATIVE.equals(entry.runtimeType())) {
                    continue; // Node/MCP 制品保持手动安装（避免启动即拉起 sidecar）
                }
                try {
                    PluginInstallationEntity existing = registry.get(entry.pluginId()).orElse(null);
                    if (existing != null && PluginLifecycleState.DISABLED.name().equals(existing.status())) {
                        continue; // 用户显式停用：不复活
                    }
                    if (existing == null) {
                        existing = registry.install(new PluginInstallationEntity(
                                entry.pluginId(), entry.displayName(), entry.version(),
                                entry.runtimeType(), entry.sourcePath(), entry.entrypoint(),
                                PluginLifecycleState.REGISTERED.name()));
                        log.info("插件目录自动登记: {}", entry.pluginId());
                    }
                    if (registry.runtimeState(entry.pluginId()) != PluginLifecycleState.ACTIVE) {
                        registry.activate(entry.pluginId());
                        log.info("插件目录自动激活: {}", entry.pluginId());
                    }
                } catch (RuntimeException e) {
                    log.warn("插件目录自动装载失败（继续启动）: {}: {}", entry.pluginId(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("插件目录扫描失败（继续启动）: {}", e.getMessage());
        }
    }

    /** DB 恢复：status=ACTIVE 的记录逐一激活；失败标 FAILED 并继续（不阻断启动）。 */
    private void restoreActiveRecords() {
        for (PluginInstallationEntity record : registry.list()) {
            if (!PluginLifecycleState.ACTIVE.name().equals(record.status())) {
                continue;
            }
            if (registry.runtimeState(record.pluginId()) == PluginLifecycleState.ACTIVE) {
                continue; // 目录自动装载已激活
            }
            try {
                registry.activate(record.pluginId());
                log.info("插件启动激活成功: {}", record.pluginId());
            } catch (RuntimeException e) {
                log.warn("插件启动激活失败（已标 FAILED，继续启动）: {}: {}", record.pluginId(), e.getMessage());
            }
        }
    }

    /** classpath ServiceLoader 发现（开发/测试通道）；同 id 已激活（jar 通道优先）则跳过。 */
    private void activateClasspathPlugins() {
        Map<String, PluginManifest> manifests = classpathManifests();
        for (JavaHarnessPlugin plugin : ServiceLoader.load(JavaHarnessPlugin.class, getClass().getClassLoader())) {
            String pluginId = plugin.pluginId();
            if (pluginHost.activeIds().contains(pluginId)) {
                continue;
            }
            PluginManifest manifest = manifests.getOrDefault(pluginId,
                    new PluginManifest(pluginId, pluginId, "0.0.0", "jnf",
                            "classpath plugin", plugin.getClass().getName()));
            if (manifest.sdkVersion() > SdkVersion.CURRENT) {
                log.warn("classpath 插件 {} 需要 SDK v{}，当前 v{}，跳过",
                        pluginId, manifest.sdkVersion(), SdkVersion.CURRENT);
                continue;
            }
            try {
                pluginHost.activateLoaded(new IPluginLoaderPort.LoadedPlugin(manifest, plugin, null));
                log.info("classpath 插件已激活: {}", pluginId);
            } catch (RuntimeException e) {
                log.warn("classpath 插件激活失败（继续启动）: {}: {}", pluginId, e.getMessage());
            }
        }
    }

    /** classpath 上的 META-INF/plugin.yaml（按 id 归并；保留 toolNamespace/sdkVersion 语义）。 */
    private Map<String, PluginManifest> classpathManifests() {
        Map<String, PluginManifest> byId = new HashMap<>();
        try {
            Enumeration<URL> resources =
                    getClass().getClassLoader().getResources("META-INF/plugin.yaml");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    Map<String, String> values = JavaPluginLoader.parseFlatYaml(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    String id = values.get("id");
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    int sdkVersion;
                    try {
                        sdkVersion = Integer.parseInt(values.getOrDefault("sdkVersion", "1").trim());
                    } catch (NumberFormatException e) {
                        sdkVersion = 1;
                    }
                    byId.put(id, new PluginManifest(id,
                            values.getOrDefault("name", id),
                            values.getOrDefault("version", "0.0.0"),
                            values.getOrDefault("author", ""),
                            values.getOrDefault("description", ""),
                            values.getOrDefault("entrypoint", ""),
                            sdkVersion,
                            values.getOrDefault("toolNamespace", PluginManifest.NAMESPACE_PLUGIN)));
                }
            }
        } catch (Exception e) {
            log.warn("classpath 插件清单解析失败（继续）: {}", e.getMessage());
        }
        return byId;
    }
}
