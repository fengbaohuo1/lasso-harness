---
name: jnf-harness-plugin-dev
description: 给 jnf-harness 编写并上线 Java 插件 JAR：SPI 骨架、plugin.yaml 清单、工具/端点/Hook/配置注册、打包与 install-activate-reload REST、报错对照。含可复制的最小模板。
when_to_use: 当用户要在 jnf-harness 里新增插件、把内部能力暴露成插件工具、注册插件 HTTP 端点或配置页、排查插件未加载/激活失败/sdkVersion 不匹配/工具名冲突时。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: plugin
---

# jnf-harness 插件开发（Java SPI）

本技能回答：插件长什么样、怎么打包、怎么装进宿主、怎么排错。所有类名/端点/报错均在仓库代码中核实过；带「未确认」标记的内容不要当成事实使用。

## 0. 边界与前置事实

- Java 17、Spring Boot 3.3.5、Maven 聚合工程；插件是**独立 JAR**，不必成为宿主的 Maven 子模块。
- 插件只依赖 SDK 模块 `jnf-harness-types`（`scope=provided`），不要把它打进插件 JAR；宿主内核 fat jar **不含**插件，官方插件放 `jnf-harness-app/target/plugins/`，启动时自动装载。
- SDK 版本：`com.jnf.harness.spi.SdkVersion.CURRENT = 1`；清单 `sdkVersion` 高于 1 会被拒绝加载。
- 参考实现（最佳模板）：`plugins/sample/src/main/java/com/jnf/harness/sample/SampleToolsPlugin.java` 与 `plugins/sample/src/main/resources/META-INF/`。
- 工程位置：**官方插件**模块统一放 `plugins/<短名>/`（如 `plugins/fs`），`artifactId` 用 `jnf-harness-plugin-<名>`，并加入根 `pom.xml` 的 `<modules>`；
  **第三方/演示插件**保持独立工程（放仓库外），只依赖 `jnf-harness-types`，构建后把 jar 放进宿主插件目录即可。

## 1. SDK 位置（先读这些类）

模块 `jnf-harness-types`，包 `com.jnf.harness.spi`：

| 类型 | 作用 |
| --- | --- |
| `JavaHarnessPlugin` | 插件入口接口：`pluginId()` / `tools()` / `configure(PluginContext)` / `onStart()` / `onStop()` |
| `AbstractHarnessPlugin` | 基类：构造传 pluginId；`configure` 默认把 `tools()` 全部注册；子类覆写时必须先 `super.configure(context)` |
| `PluginContext` | 事务边界：注册工具/端点/Hook/提示词/事件/Disposer，读配置 |
| `PluginManifest` | 清单记录：id/name/version/author/description/entrypoint/sdkVersion/toolNamespace |
| `PluginHook` / `PluginHookResult` | `PRE_TOOL_USE` / `POST_TOOL_USE` 裁决（ALLOW/BLOCK/DENY） |
| `PluginConfigStore` | 插件配置 KV 端口（宿主实现落库） |
| `spi.host.*` | 宿主服务：`PluginHttpHandler`/`PluginHttpRequest`/`PluginHttpResponse`、`ShellService`、`WebService`、`AgentRunService`、`SessionSearchService` |

包 `com.jnf.harness.model`：`ToolDefinition`、`AbstractTool`、`ToolSchema`、`ToolExecutionResult`、`ToolRunContext`。

## 2. 最小插件骨架（逐文件）

目录（以插件 id `my-plugin` 为例，包名可自定）：

```
my-plugin/
├─ pom.xml
└─ src/main/
   ├─ java/com/example/myplugin/MyPlugin.java
   └─ resources/META-INF/
      ├─ plugin.yaml
      └─ services/com.jnf.harness.spi.JavaHarnessPlugin
```

### 2.1 pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.jnf.harness</groupId>
    <artifactId>jnf-harness</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../jnf-harness/pom.xml</relativePath>
  </parent>
  <artifactId>my-plugin</artifactId>
  <dependencies>
    <dependency>
      <groupId>com.jnf.harness</groupId>
      <artifactId>jnf-harness-types</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
```

若插件目录在仓库外，去掉 `parent`，给 `jnf-harness-types` 显式写 `<version>0.1.0-SNAPSHOT</version>`；slf4j-api 用与 Spring Boot 3.3.5 兼容的 2.0.x，`provided`。

### 2.2 插件类

```java
package com.example.myplugin;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginHookResult;
import com.jnf.harness.spi.host.PluginHttpResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MyPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "my-plugin";

    public MyPlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new EchoTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context); // 必须：注册 tools()

        context.registerSystemPrompt("about", 100,
                "## my-plugin\n- echo 工具回显输入，用于连通性验证。");

        context.registerHook("PRE_TOOL_USE", (toolName, arguments) -> {
            if ("shell_execute".equals(toolName)) {
                return PluginHookResult.allow(); // 改成 block(reason)/deny(reason) 即拦截
            }
            return PluginHookResult.allow();
        });

        context.registerEndpoint("GET", "stats",
                request -> PluginHttpResponse.ok(Map.of("ok", true)));

        context.registerDisposer(() -> {
            // 停用/卸载/重载时由宿主调用：关闭线程池、连接、文件句柄
        });
    }

    static class EchoTool extends AbstractTool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "回显传入的 text，用于插件连通性验证。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("text", stringSchema("要回显的文本"))
                    .required("text")
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String text = str(args, "text");
            if (text.isBlank()) {
                return fail("text 不能为空", "MISSING_TEXT");
            }
            return ok("{\"echo\":\"" + text.replace("\"", "\\\"") + "\"}");
        }
    }
}
```

工具约定：
- `name()` 写**裸名**（如 `echo`）。宿主默认注册为限定名 `plugin__<pluginId>__<echo>`；清单 `toolNamespace: global` 时保留裸名（全局唯一，重名注册报 `工具名已被占用: <name>`）。
- `parameters()` 用 `objectSchema()` + `stringSchema` / `numberSchema(desc, integer)` / `booleanSchema` 构造。
- `run` 返回 JSON 字符串最利于前端渲染；失败用 `fail(msg, CODE)`，不要抛异常（基类兜底成 `EXECUTION_ERROR`）。
- 相对路径以 `ctx.cwd()` 为基准；`ctx` 还提供 `agentId()`、`sessionId()`。
- 不要长期持有宿主对象引用；注册项由宿主逆序回收。

### 2.3 META-INF/plugin.yaml

```yaml
id: my-plugin
name: My Plugin
version: 1.0.0
author: your-name
description: 一句话说明插件能力
entrypoint: com.example.myplugin.MyPlugin
sdkVersion: 1
toolNamespace: plugin
ui: META-INF/plugin-ui.json
```

- **必须是扁平 `key: value`**（宿主解析器不支持嵌套/多行/列表；缩进行会被当成顶层键）。只有单行值；行首 `#` 是注释；值两侧成对引号会被剥掉。
- 必填 `id`；可选 `name`（缺省=id）、`version`（缺省 `0.0.0`）、`entrypoint`、`sdkVersion`（缺省 1）、`toolNamespace`（缺省 `plugin`）、`ui`（缺省 `META-INF/plugin-ui.json`）。
- `entrypoint` 类必须 `public` + **无参构造**；类上的 `pluginId()` 返回值必须等于清单 `id`。
- `entrypoint` 为空时走 `ServiceLoader`（`META-INF/services/com.jnf.harness.spi.JavaHarnessPlugin`）；建议同时提供两者，加载器优先 `entrypoint`。
- 没有入口但 `ui` 指向的清单存在 → 允许 UI-only 插件；两者都没有会报 `未找到插件入口（entrypoint 或 META-INF/services 均缺失）且无 UI 清单`。

`META-INF/services/com.jnf.harness.spi.JavaHarnessPlugin` 内容（每行一个实现类全名）：

```
com.example.myplugin.MyPlugin
```

插件 UI 清单（工具卡渲染器/独立设置页/斜杠命令/@ 提及）是可选的，字段见 `references/plugin-ui-manifest.md`；不需要前端贡献就删掉 `ui:` 行。

## 3. 打包（PowerShell 优先，bash 等价）

先让本地仓库有父 POM 与 SDK（从 `<workspace>/jnf-harness` 执行）：

```powershell
mvn -N install                     # 只装父 POM
mvn -pl jnf-harness-types install -DskipTests
mvn -f <插件目录>\pom.xml clean package
# 产物：<插件目录>\target\<artifactId>-<version>.jar
```

bash 等价：同样三条命令，把 `\` 换成 `/`。

- 若坚持把插件做成仓库内子模块：需要在根 `pom.xml` 的 `<modules>` 里加模块（**属于改仓库构建，先征得维护者同意**），之后用 `mvn -pl <module> -am package -DskipTests`。
- 验证 JAR 内容：`jar tf <插件>.jar` 应能看到 `META-INF/plugin.yaml`、`META-INF/services/...`、插件类；不应出现 `com/jnf/harness/spi/*` 或 SLF4J 实现类（provided 未生效才会出现）。

## 4. 安装 / 启用 / 重载（REST）

所有管理端点前缀 `/api/harness/plugins`，响应统一为 `{code, info, data}`，**HTTP 恒 200**，用 `code == "00000"` 判断成功。若宿主设了 `harness.auth.api-keys`，请求头带 `X-API-Key: <key>`（或 `Authorization: Bearer <key>`）。

两条上线路径：

1. **放目录 + 重启**：把 JAR 放进插件目录（解析顺序：`harness.plugins.dir` → 内核 jar 同级 `plugins/` → 进程 cwd 下 `./plugins`），启动时 `harness.plugins.auto-load`（默认 true）自动 install+activate。
2. **REST 登记 + 激活**（不重启；适合开发迭代）：

```powershell
$base = "http://localhost:8090"
$h = @{ "X-API-Key" = "<key>" }   # api-keys 为空时可不带

# 登记：只写安装记录，不激活。sourcePath 可写绝对路径；若 JAR 已在插件目录内，写文件名即可
Invoke-RestMethod -Method Post "$base/api/harness/plugins/install" -Headers $h -ContentType "application/json" -Body (@{
  pluginId = "my-plugin"; displayName = "My Plugin"; pluginVersion = "1.0.0"
  runtimeType = "JAVA_NATIVE"; sourcePath = "target\my-plugin-1.0.0.jar"
  entrypoint = "com.example.myplugin.MyPlugin"
} | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/api/harness/plugins/activate" -Headers $h -ContentType "application/json" -Body (@{ pluginId = "my-plugin" } | ConvertTo-Json)
```

其它端点（`{id}` = pluginId）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/harness/plugins` | 列表：status(REGISTERED/ACTIVE/FAILED...)、runtimeState、sourcePath、hasUi |
| GET | `/api/harness/plugins/{id}/status` | 运行态 |
| GET | `/api/harness/plugins/catalog` | 扫描插件目录，含 installed 标记 |
| GET | `/api/harness/plugins/{id}/contributions` | 工具清单、端点、UI、slash/mention 贡献 |
| POST | `/api/harness/plugins/{id}/disable` | 停用并逆序回收注册项 |
| POST | `/api/harness/plugins/{id}/reload` | 停用→按记录重新激活；替换 JAR 后用 |
| DELETE | `/api/harness/plugins/{id}` | 停用 + 删安装记录（不删 JAR） |
| GET/PUT | `/api/harness/plugins/{id}/config` | 读/合并写插件配置（PUT body 为 `Map<String,String>`） |
| DELETE | `/api/harness/plugins/{id}/config/{configKey}` | 删单个配置键 |
| GET | `/api/harness/plugins/{id}/ui` | 该插件 UI 清单（无则 data=null） |

替换 JAR 的流程：覆盖同名 JAR（若 sourcePath 是插件目录内的相对文件名）→ `POST /{id}/reload`。若 install 时用了目录外绝对路径，覆盖后同样 reload（记录里存的是该路径）。激活失败会落 `FAILED`，修复后先看 `/status` 再 reload。

## 5. 配置注入

插件配置由宿主持久化（`harness_plugin_config`），按 pluginId 隔离：

```java
@Override
public void configure(PluginContext context) {
    super.configure(context);
    String baseUrl = context.getConfig("baseUrl", "https://example.com"); // 读不到给默认值
}
```

- `AbstractHarnessPlugin` 提供 `protected String getConfig(String key, String defaultValue)`，`configure()` 之后可用。
- 外部写入：`PUT /api/harness/plugins/{id}/config`，body 如 `{"baseUrl":"https://example.com"}`，合并保存并回显全量 map。
- `getConfig` 每次读存储，配置改完无需重启插件（依赖你即用即读）。
- 不要把密钥写进 `plugin.yaml` 或代码；需要上游密钥时用配置项 + `env:`/`secret:` 引用（宿主密钥存储 `data/secrets.json` 是 Base64 混淆，非 HSM）。

## 6. Hook / 事件 / 提示词 / 端点

- Hook 点只有两个：`PRE_TOOL_USE`、`POST_TOOL_USE`（其它值报 `未知 hookPoint: <x>`）。PRE 按注册顺序执行，首个 `intercepted()`（BLOCK/DENY）即停；被拦截时模型看到失败工具结果（`HOOK_BLOCKED`，message 前缀 `Blocked by tool hook:`），不是进程拒绝；POST 只观察。**Hook 抛异常只会 `log.warn` 并继续**，不能当硬安全边界。
- `context.subscribe(eventName, handler)` / `context.emit(eventName, payload)`：事件总线。
- `context.registerSystemPrompt(name, order, text)`：段落名 `plugin__<pluginId>__<name>`，`order` 控制拼接位置（sample 用 100）。提示词应写"能力 + 何时调用"，不要塞长文档。
- `context.registerEndpoint(method, path, handler)`：实际挂载 `/api/harness/plugins/<pluginId>/api/<path>`；`PluginHttpRequest(method, path, query, bodyJson)`，用 `PluginHttpResponse.ok(body)` / `error(status, message)` 返回；宿主统一信封。旧宿主无端点注册表时会抛明确 UnsupportedOperationException。

## 7. 常见坑（按报错定位）

完整的“报错字面文本 → 原因 → 处理”对照表见 `references/common-errors.md`（用 `fs_read` 读取本技能目录下该文件）。高频几条：

- `插件 X 需要 SDK vN，当前宿主支持 vM`：`sdkVersion` 写高了，当前宿主只支持 1。
- `pluginId 不一致: 清单=<id> 实现=<pluginId>`：清单 id 与 `super(...)` 不一致。
- `未找到插件入口（entrypoint 或 META-INF/services 均缺失）且无 UI 清单`：清单漏 `entrypoint` 且无 services。
- ACTIVE 但模型看不到工具：默认命名空间下工具名是 `plugin__<id>__<tool>`。
- 覆盖 JAR 后行为没变：必须 `reload`（disable→activate 才会新建类加载器）。

## 8. 自检清单

- [ ] `plugin.yaml` 扁平、必填 `id`、`entrypoint` 与类全名一致、`sdkVersion: 1`
- [ ] `pluginId()` 与清单 id 一致；入口类有无参构造
- [ ] `configure` 里先 `super.configure(context)`；注册项都可回收
- [ ] 工具 name 裸名唯一；参数 schema 完整；失败走 `fail(...)`
- [ ] JAR 里无 `jnf-harness-types` / SLF4J 实现；`jar tf` 检查 `META-INF/plugin.yaml`
- [ ] `GET /api/harness/plugins` 状态 ACTIVE、`/contributions` 能看到工具
- [ ] 让模型调用一次限定名工具；停用后再查工具已消失
- [ ] 配置项缺省值可用，PUT 后行为变化
