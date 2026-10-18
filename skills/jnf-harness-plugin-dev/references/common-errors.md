# jnf-harness 插件常见报错与处理

按报错字面文本定位。报错来源：`JavaPluginLoader`、`PluginRegistryService`、`PluginHost`、`HostPluginContext`、`ToolHookRegistry`、`ToolCallExecutor`。

| 现象/报错 | 原因与处理 |
| --- | --- |
| `插件 X 需要 SDK vN，当前宿主支持 vM，请升级内核` | `plugin.yaml` 的 `sdkVersion` 写高了；当前宿主 `SdkVersion.CURRENT = 1` |
| `pluginId 不一致: 清单=<id> 实现=<pluginId>` | `plugin.yaml` 的 `id` 与类里 `super(...)` / `pluginId()` 不一致 |
| `缺少 META-INF/plugin.yaml` | 清单资源没打进 JAR；检查 `src/main/resources/META-INF/plugin.yaml` |
| `plugin.yaml 缺少必填字段: id` | 清单漏 `id`，或 `id:` 前有 BOM/缩进导致没解析到 |
| `plugin.yaml sdkVersion 非法: <raw>` | `sdkVersion` 必须是整数 |
| `未找到插件入口（entrypoint 或 META-INF/services 均缺失）且无 UI 清单` | 清单没写 `entrypoint`、没有 services 文件，也没有可用的 `ui:` 清单 |
| `制品不存在: <path>` | `POST /install` 的 `sourcePath` 既不是有效路径，也不是插件目录内的文件名 |
| `插件未安装: <id>` | 先 install 再 activate；或 pluginId 拼错 |
| `插件制品不存在: ...（尝试目录重解析后仍失败）` | 安装记录里的 JAR 已不在插件目录，重新 install 或把 JAR 放回 |
| `插件激活失败: <id>: ...` | `configure`/`onStart` 抛异常；状态置 FAILED，看日志堆栈，修复后 reload |
| `插件已激活: <id>` | 重复 activate；先 disable 或直接 reload |
| `Node Bridge 未启用` | `runtimeType` 写了 `DSH_NODE_BRIDGE` 但宿主未启用 Node 通道；Java 插件改回 `JAVA_NATIVE` |
| 列表里 ACTIVE 但模型没有工具 | 工具在插件命名空间下，模型看到的是 `plugin__<id>__<tool>`；或工具注册失败/已被回收 |
| `工具名已被占用: <name>` | `toolNamespace: global` 要求全局唯一；改回默认 `plugin` 命名空间 |
| 重启后插件不生效 | auto-load 只在启动时扫插件目录（`harness.plugins.auto-load` 默认 true；DISABLED 不复活）；失败只 `log.warn` 不阻断启动，查插件目录解析与日志 |
| `NoClassDefFoundError` / `ClassNotFoundException` | 插件依赖了宿主内部类，或把 types/其它依赖打进了 JAR；只依赖 `jnf-harness-types`（provided） |
| 日志不输出 | 用 `org.slf4j.LoggerFactory`；不要自带 SLF4J 实现（binding 与宿主冲突） |
| `当前宿主未装配插件端点注册表` | 由旧宿主/裁剪宿主启动，`registerEndpoint` 不可用 |
| 热重载后仍是旧行为 | 类加载器隔离下需 reload（disable→activate 新建类加载器并关闭旧的）；直接覆盖 JAR 不自动生效 |
| UI 配置页不出现 | `plugin-ui.json` 缺 `settingsPage` 或 `ui:` 路径不对；用 `GET /{id}/contributions` 看 `hasSettingsPage` |
| `未知 hookPoint: <x>` | 只支持 `PRE_TOOL_USE` / `POST_TOOL_USE` |
| Hook 拦截没生效 | Hook 抛异常只会 `log.warn` 并继续；核对 toolName 是否为限定名，且注册在 PRE 点 |
