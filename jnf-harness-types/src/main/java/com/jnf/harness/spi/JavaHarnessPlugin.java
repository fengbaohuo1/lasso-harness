package com.jnf.harness.spi;

import com.jnf.harness.model.ToolDefinition;

import java.util.List;

/**
 * Java 原生插件入口契约（SPI）。
 * <p>插件 JAR 需包含：
 * <ul>
 *   <li>{@code META-INF/plugin.yaml} —— id/name/version/entrypoint 元数据；</li>
 *   <li>{@code META-INF/services/com.jnf.harness.spi.JavaHarnessPlugin} —— ServiceLoader 注册入口类。</li>
 * </ul>
 */
public interface JavaHarnessPlugin {

    /** 插件唯一标识（须与 plugin.yaml 的 id、安装请求的 pluginId 一致）。 */
    String pluginId();

    /** 插件贡献的工具集（默认经 {@link AbstractHarnessPlugin#configure} 自动注册）。 */
    List<ToolDefinition> tools();

    /** 宿主注入上下文：注册工具/提示词/Hook/事件/Disposer。基类实现注册 tools()。 */
    void configure(PluginContext context);

    /** 插件启动完成回调。 */
    default void onStart() {
    }

    /** 插件停止回调（宿主还会逆序回收注册项，此处只做插件自身收尾）。 */
    default void onStop() {
    }
}
