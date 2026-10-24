# 应用 + 插件最小骨架（TEMPLATE）

一个能跑的最小闭环：**Spring Boot 应用画界面 → 应用服务端代理 Agent SSE → Java 插件把应用 REST 桥成工具**。
复制整个 `app-plugin/` 目录改名即可作为新项目起点；本文件只讲「怎么跑起来、怎么改名、怎么验证」。

## 0. 目录结构

```
app-plugin/
├─ pom.xml                      # 聚合 POM（Java 17，Spring Boot 3.3.5 BOM）
├─ TEMPLATE.md                  # 本文件
├─ sample-app/                  # 业务应用
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/example/appplugin/
│     │  ├─ AppApplication.java          # 启动类
│     │  ├─ DemoController.java         # GET /api/demo/items（5 条内存数据，统一信封）
│     │  └─ AssistantProxyController.java # POST /api/assistant/stream（SSE 原样透传）
│     └─ resources/
│        ├─ application.yml             # 端口 18081 + harness.base-url/api-key
│        └─ static/index.html           # 极简 AI 聊天页（复杂 UI 看技能正文/参考文档）
└─ sample-plugin/               # jnf-harness Java 插件（只做桥）
   ├─ pom.xml
   └─ src/main/
      ├─ java/com/example/appplugin/plugin/DemoBridgePlugin.java
      └─ resources/META-INF/
         ├─ plugin.yaml
         └─ services/com.jnf.harness.spi.JavaHarnessPlugin
```

产物（固定名，脚本按这个名字传参）：
- 应用：`sample-app/target/sample-app.jar`
- 插件：`sample-plugin/target/sample-plugin.jar`

## 1. 构建前置

| 依赖 | 说明 |
| --- | --- |
| JDK 17+ | `java -version` 确认 |
| Maven 3.6+ | 本地仓库已有 Spring Boot 3.3.5 时可用 `-o` 离线构建 |
| 底座 jar | `<jnf-harness>/jnf-harness-app/target/jnf-harness-app.jar`（未构建：在 jnf-harness 仓库执行 `mvn -o -q -DskipTests package`） |
| 插件 SDK | `com.jnf.harness:jnf-harness-types:<版本>` 必须在本地仓库 |

SDK 不在本地仓库时，先在 **jnf-harness 仓库**安装（离线加 `-o`）：

```bash
cd <jnf-harness>
mvn -q -N install
mvn -q -pl jnf-harness-types install -DskipTests
```

SDK 版本不是 `0.1.0-SNAPSHOT` 时，覆盖属性构建：`mvn -Djnf-harness-types.version=<版本> ...`。

## 2. 构建

```bash
cd app-plugin
mvn -o -q -DskipTests package
```

两模块产物：`sample-app/target/sample-app.jar`、`sample-plugin/target/sample-plugin.jar`。

## 3. 启动应用

```bash
java -jar sample-app/target/sample-app.jar --server.port=18081
```

- 页面：`http://127.0.0.1:18081/`（输入框 + 消息区，调应用自己的 `/api/assistant/stream`）
- 业务接口：`http://127.0.0.1:18081/api/demo/items`（统一信封 `{code,info,data}`）
- 代理接口：`POST /api/assistant/stream`，body 只取 `{message, agentId?}`；底座地址与 API Key 由 `application.yml` 的 `harness.base-url` / `harness.api-key` 决定（可用 `HARNESS_BASE_URL` / `HARNESS_API_KEY` 覆盖）。

> 端口一律显式 `--server.port=`，避免环境变量 `SERVER_PORT` 劫持。
> 底座未配置模型渠道时，代理会原样透传 `event: error`——先到 `http://127.0.0.1:8090/` 的「设置 → 模型」配渠道。

## 4. 安装插件（两条路，任选）

**A. 放目录 + 重启**（交付机最省事）：把 `sample-plugin.jar` 复制到 **底座 jar 同级** 的 `plugins/` 目录，重启底座即自动 install + activate。

**B. REST 登记 + 激活**（开发迭代，不重启）：

```bash
node <skill-path>/scripts/install_plugin.mjs \
  --base http://127.0.0.1:8090 \
  --jar <项目绝对路径>/app-plugin/sample-plugin/target/sample-plugin.jar \
  --plugin-id demo-bridge \
  --entrypoint com.example.appplugin.plugin.DemoBridgePlugin \
  --version 1.0.0
```

脚本会做 catalog → install（sourcePath 绝对路径）→ activate（已激活幂等）→ 打印 contributions 的工具名与端点。
默认工具名是限定名 `plugin__demo-bridge__demo_list_items`；想让模型看到裸名，在 `plugin.yaml` 加 `toolNamespace: global`（自行保证全局唯一）。

应用地址默认 `http://127.0.0.1:18081`，改端口后同步插件配置（无需重启插件）：

```bash
curl -X PUT http://127.0.0.1:8090/api/harness/plugins/demo-bridge/config \
  -H "Content-Type: application/json" -d "{\"app.base-url\":\"http://127.0.0.1:18083\"}"
```

## 5. 验证（按顺序跑）

```bash
# 1) 底座冒烟 + 插件状态
node <skill-path>/scripts/smoke_test.mjs --base http://127.0.0.1:8090 --plugin demo-bridge

# 2) Agent 端到端（工具轨迹 + done.text）
node <skill-path>/scripts/agent_stream.mjs --base http://127.0.0.1:8090 \
  --message "调用 demo_list_items 列出全部条目，并汇总库存总数"

# 3) 应用代理 SSE（不经过浏览器）
curl -N -X POST http://127.0.0.1:18081/api/assistant/stream \
  -H "Content-Type: application/json" -d "{\"message\":\"列出条目\"}"

# 4) 交付自动检查（含应用探活）
node <skill-path>/scripts/delivery_check.mjs --plugin demo-bridge --app-url http://127.0.0.1:18081
```

预期：`smoke_test` 全 PASS；`agent_stream` 出现 `[step_break] → demo_list_items` 且 `done.status` 正常；
工具返回的数字与 `GET /api/demo/items` 一致（口径同源）。没配模型时第 2、3 步会收到 `event: error`，属预期。

## 6. 改成自己的项目（改名清单）

| 位置 | 改什么 |
| --- | --- |
| 根 `pom.xml` | `groupId` / `artifactId` / `version` |
| `sample-app`、`sample-plugin` 目录名与 `pom.xml` 的 `<artifactId>` | 项目名 |
| 根 `pom.xml` 的 `<modules>` | 同步模块目录名 |
| Java 包 `com.example.appplugin` | 换成 `com.<公司>.<领域>`（IDE 重命名即可） |
| `AppApplication` / `application.yml` | 应用名、端口（18081 起顺延，避开底座 8090） |
| `DemoController` | 换成真实实体/REST（保留统一信封与种子数据） |
| `index.html` | 换成业务界面；AI 面板复杂实现见技能 `references/` |
| `sample-plugin/pom.xml` 的 `<finalName>` | 插件 jar 名（install 脚本的 `--jar` 要对应） |
| `plugin.yaml` 的 `id` / `name` / `entrypoint` + `DemoBridgePlugin.PLUGIN_ID` | 四处必须一致：清单 id = 类 `pluginId()` = 安装脚本 `--plugin-id` |
| `DemoBridgePlugin` | 改成调你自己的应用 REST；工具 `name` 用业务前缀，description 写清「何时必须调/返回什么」 |
| `services/com.jnf.harness.spi.JavaHarnessPlugin` | 入口类全名，与 `entrypoint` 一致 |

## 7. 常见问题

| 现象 | 处理 |
| --- | --- |
| `制品不存在` | `--jar` 路径写错；先用绝对路径确认文件存在 |
| `未找到插件入口` | `entrypoint` 与类全名不一致，或 jar 内缺 `META-INF/services` 文件 |
| 插件 ACTIVE 但模型看不到工具 | 默认是限定名 `plugin__<id>__<tool>`；让模型按限定名调用，或 `toolNamespace: global` |
| 工具返回「连不上应用」 | 应用没起 / 端口不对；`GET /api/demo/items` 先手工验证，再改插件配置 `app.base-url` |
| 代理 SSE 没输出 | 底座 `harness.base-url` 或 `harness.api-key` 不对；看应用日志里的 `PROXY_ERROR` 帧文案 |
| 改了插件 jar 行为没变 | `install_plugin.mjs --reload`（或 `POST /api/harness/plugins/demo-bridge/reload`）：类加载器要重建 |
| 构建报缺 `jnf-harness-types` | 回到「构建前置」把 SDK install 到本地仓库 |

## 8. 边界

- 不做认证/权限/数据库：本骨架只为打通「应用 ↔ 插件 ↔ Agent」链路，业务安全按项目需求自己加。
- `index.html` 的 SSE 解析是**最小演示**（chunk 累积 + 工具轨迹 + 错误提示）；markdown 渲染、AbortController、去重后的工具卡等完整实现见技能正文与 `references/`。
- 插件只调应用 REST，不直连数据库；应用 REST 是唯一数据源，页面与工具口径必须一致。
