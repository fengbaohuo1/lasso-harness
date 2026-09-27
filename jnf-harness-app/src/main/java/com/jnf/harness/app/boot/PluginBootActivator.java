package com.jnf.harness.app.boot;

import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.spi.PluginLifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * P3：启动恢复——status=ACTIVE 的插件安装记录逐一自动激活；
 * 失败标 FAILED 并继续（不阻断启动）。
 */
public class PluginBootActivator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginBootActivator.class);

    private final PluginRegistryService registry;

    public PluginBootActivator(PluginRegistryService registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (PluginInstallationEntity record : registry.list()) {
            if (!PluginLifecycleState.ACTIVE.name().equals(record.status())) {
                continue;
            }
            try {
                registry.activate(record.pluginId());
                log.info("插件启动激活成功: {}", record.pluginId());
            } catch (RuntimeException e) {
                log.warn("插件启动激活失败（已标 FAILED，继续启动）: {}: {}", record.pluginId(), e.getMessage());
            }
        }
    }
}
