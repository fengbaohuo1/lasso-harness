package com.jnf.harness.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 插件上下文：插件贡献项的事务边界。
 * <p>所有注册项由宿主统一管理，插件停止/卸载/热重载时逆序回收；
 * 插件不应长期持有宿主组件引用。
 */
public interface PluginContext {

    /** 当前插件 ID。 */
    String pluginId();

    /** 注册工具（限定名为 plugin__&lt;pluginId&gt;__&lt;toolName&gt;）。返回注销器。 */
    AutoCloseable registerTool(com.jnf.harness.model.ToolDefinition tool);

    /** 登记自定义资源释放器（连接池/线程池等），停用时由宿主执行。 */
    void registerDisposer(AutoCloseable disposer);

    /** 订阅插件事件；返回取消订阅器。 */
    AutoCloseable subscribe(String eventName, PluginEventHandler handler);

    /** 以当前插件为来源发布事件。 */
    void emit(String eventName, Object payload);

    /** 注册 Hook；hookPoint 取值：PRE_TOOL_USE / POST_TOOL_USE。返回注销器。 */
    AutoCloseable registerHook(String hookPoint, PluginHook hook);

    /** 读取宿主注入的插件配置。 */
    Optional<String> getConfig(String key);

    /** 注册系统提示词段落（段落名 plugin__&lt;pluginId&gt;__&lt;name&gt;，order 控制拼接顺序）。 */
    AutoCloseable registerSystemPrompt(String name, int order, String text);

    /** 便捷：读配置并给默认值。 */
    default String getConfig(String key, String defaultValue) {
        return getConfig(key).orElse(defaultValue);
    }
}
