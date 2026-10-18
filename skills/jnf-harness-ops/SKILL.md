---
name: jnf-harness-ops
description: jnf-harness 本地构建、启动、鉴权、模型渠道、数据目录、技能/插件目录与常见故障速查；命令以 Windows PowerShell 为主并给 bash 等价。
when_to_use: 当需要构建或启动 jnf-harness、改端口/API Key、配置模型渠道、定位 data 与日志、排查端口占用、H2 锁、401、技能不出现、插件不加载、schema 缺列时。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: ops
---

# jnf-harness 本地运维速查

对外名 `lasso-harness`，模块/包名仍是 `jnf-harness-*` / `com.jnf.harness`。以下配置键、端点、目录均经代码核实；标注「未确认」的不要当事实。

## 1. 构建

在仓库根 `<workspace>/jnf-harness` 执行（无 `mvnw`，用本机 Maven 3.9 + JDK 17）：

```powershell
mvn clean verify                # 构建 + 全部测试（含 ArchUnit 分层门禁）
mvn clean package -DskipTests   # 快速产物（Dockerfile / package-local.mjs 用这个）
```

bash 等价：

```bash
mvn clean verify
mvn clean package -DskipTests
```

产物：

- 内核 fat jar：`jnf-harness-app/target/jnf-harness-app.jar`
- 官方插件：`jnf-harness-app/target/plugins/*.jar`（构建时由 `maven-dependency-plugin` 拷贝）
- 多 jar 发布包：`node scripts/package-local.mjs` → `dist/jnf-harness-local/`（jar + `plugins/` + `plugin-sdk/` + `start.bat` / `start.sh`）

只构建某模块：`mvn -pl jnf-harness-trigger -am package -DskipTests`。

## 2. 启动

```powershell
java -version    # 必须 17+
java -jar jnf-harness-app\target\jnf-harness-app.jar
```

```bash
java -jar jnf-harness-app/target/jnf-harness-app.jar
```

- 默认端口 **8090**（`application.yml` 的 `server.port`），Web 控制台 `http://localhost:8090/`。
- 改端口：`--server.port=8091`（或环境变量 `SERVER_PORT`）；改图省事的完整写法：

```powershell
java -jar jnf-harness-app\target\jnf-harness-app.jar --server.port=8091 --spring.profiles.active=standalone
```

- `--spring.profiles.active=standalone` 只是 Dockerfile/package-local/桌面壳统一带上的参数；**仓库没有 `application-standalone.yml`**，它不改变任何配置。
- 数据目录默认相对于**进程工作目录**：`./data/`。从仓库根启动就落在 `jnf-harness/data/`。
- 单实例假设：Agent 运行态在内存，不要对同一 `./data` 起两个进程。

Windows 后台启动（新窗口）：

```powershell
Start-Process java -ArgumentList '-jar','jnf-harness-app\target\jnf-harness-app.jar' -WorkingDirectory (Get-Location)
```

## 3. 鉴权、健康检查、响应包

- 配置键 `harness.auth.api-keys`（环境变量 `HARNESS_API_KEYS`），逗号分隔；**为空 = 全部放行**。
- 请求头二选一：`X-API-Key: <key>` 或 `Authorization: Bearer <key>`。
- 免鉴权白名单（前缀）：`/actuator`、`/.well-known`、`/`、`/index*`、`/app.*`、`/lib/`、`/favicon`，以及所有 OPTIONS。
- 未带/错误 key：HTTP 401，body `{"error":"Unauthorized","message":"Invalid or missing API key"}`。
- 业务响应统一 `{ "code": "00000|40000|50000", "info": "...", "data": ... }`，**HTTP 恒 200**，要判断 `code`。
- 健康检查（actuator 已在 app 依赖中，`/actuator` 免鉴权）：

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health    # {"status":"UP"}（预期）
```

```bash
curl -s http://localhost:8090/actuator/health
```

`application.yml` 没有 `management.*` 配置，即只暴露默认端点（health/info）。

带 key 调管理接口：

```powershell
$base = "http://localhost:8090"
$h = @{ "X-API-Key" = "<key>"; "Content-Type" = "application/json" }
Invoke-RestMethod "$base/api/harness/plugins" -Headers $h
```

## 4. 模型渠道配置（REST）

前缀 `/api/harness/settings/models`（详见 `jnf-harness-trigger/src/main/java/com/jnf/harness/trigger/http/HarnessModelSettingController.java`），另有渠道模板与探测：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/harness/settings/models` | 渠道列表 |
| POST | `/api/harness/settings/models` | 新增/更新渠道（upsert） |
| GET | `/api/harness/settings/models/catalog` | 模型目录 |
| DELETE | `/api/harness/settings/models/{channelCode}` | 删除渠道 |
| POST | `/api/harness/settings/models/active` | 切默认渠道，body `{"channelCode":"..."}` |
| POST | `/api/harness/settings/models/test` | 连通性测试，返回 `{ok, latencyMs, message, models?, modelSpecs?}` |
| POST | `/api/harness/settings/models/discover` | 探测上游模型列表 |
| GET | `/api/harness/channels/presets` | 内置渠道模板（前端快捷填充，不落库） |

POST 渠道 body 字段：`channelCode`、`displayName`、`providerCode`、`modelCode`、`baseUrl`、`apiKeyRef`、`protocol`、`enabled`、`models[]`、`defaultModel`、`apiKey`。

`apiKey` 三分语义：**字段缺省 = 不改**；**空串 = 清除**；**非空 = 覆盖**。`apiKeyRef` 支持 `env:<ENV_NAME>` 与 `secret:<name>`（`secret:` 存 `data/secrets.json`，Base64 混淆，非 HSM；API 只回 `hasApiKey`，不回明文）。

示例：

```powershell
Invoke-RestMethod -Method Post "$base/api/harness/settings/models" -Headers $h -Body (@{
  channelCode = "default"; displayName = "Local"; providerCode = "openai"; protocol = "openai"
  baseUrl = "http://127.0.0.1:8777/v1"; apiKey = "<key>"
  defaultModel = "default-model"; enabled = $true
} | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/api/harness/settings/models/test" -Headers $h -Body (@{ channelCode = "default" } | ConvertTo-Json)
```

`protocol` 按宿主的协议网关取值（openai 等，见 `infrastructure/.../llm` 实现）；不确定就先用 `channels/presets` 的模板字段。

## 5. 数据与目录

| 路径（默认，相对进程 cwd） | 配置键 | 说明 |
| --- | --- | --- |
| `./data/jnf-harness.mv.db`（及 `.trace.db`） | `spring.datasource.url` | H2 文件库；`MODE=MySQL` |
| `./data/events/` | `harness.events.dir` | 会话事件溯源，每会话一个子目录 |
| `./data/secrets.json` | 硬编码（`HarnessConfiguration` 中 `Path.of("./data/secrets.json")`） | `secret:` 引用存储 |
| `./data/attachments/` | `harness.attachments.dir` | 附件目录（按需创建） |
| `./data/skills.properties` | `harness.skills.state-file` | **只记录被禁用的技能名** |
| `./plugins/` | `harness.plugins.dir` | 插件 JAR 目录；未配置时按「内核 jar 同级 `plugins/` → `./plugins`」解析 |
| `./skills/` | `harness.skills.roots`（默认 `./skills`） | 技能自定义根（本技能包所在目录） |
| `~/.jnf/skills/` | `harness.skills.user-dir` | 用户级技能（桌面端安装落这里） |
| `<cwd>/.jnf/skills/` | 无（固定） | 项目级技能，优先级最高 |

注意：

- 重置开发库 = 停进程后删除/移走 `data/jnf-harness.mv.db`（会丢任务、渠道、插件安装记录等）。
- 不要删除 `data/secrets.json` 之外还引用着 `secret:` 的渠道配置，否则密钥解析失败。
- 桌面壳（lasso-harness-desktop）会把日志写到 `<app-data>/logs/agent.log`，Windows 通常是 `%APPDATA%/com.jnf.harness.desktop/logs/agent.log`（依据桌面端 README 与 Tauri identifier `com.jnf.harness.desktop`）；`java -jar` 直接启动时日志只到 stdout。
- 桌面壳启动 Agent 时会随机生成 API Key 并传 `--harness.auth.api-keys=<key>`，同时写 `<app-data>/agent-runtime.json`（端口、PID 等）；排查端口冲突可看这个文件。

## 6. 技能运行时（配置与排障）

配置键（**只在 `@Value` 默认值里，`application.yml` 未声明**，可用命令行参数/环境变量覆盖）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `harness.skills.roots` | `./skills` | 逗号分隔的 custom 根；相对路径按**进程 cwd** 解析 |
| `harness.skills.user-dir` | 空 → `~/.jnf/skills` | 用户级技能目录 |
| `harness.skills.state-file` | `./data/skills.properties` | 禁用集合 |

发现优先级（同名时高优先级覆盖）：`<cwd>/.jnf/skills`（PROJECT）> `harness.skills.roots`（CUSTOM）> `~/.jnf/skills`（USER）。系统提示词只放目录（最多 50 条 / 4000 字符），正文由 `skill` 工具按需加载。

管理/验证端点（`cwd` 缺省为服务进程工作目录）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/harness/skills?cwd=<dir>` | 列表（name/description/whenToUse/source/path/enabled） |
| GET | `/api/harness/skills/{name}?cwd=<dir>` | 详情 + 正文 content |
| POST | `/api/harness/skills/install` | body `{gitUrl 或 zipPath, subdir, name}`，装到用户技能目录 |
| POST | `/api/harness/skills/{name}/enable` | 启用（`?cwd=` 可选） |
| POST | `/api/harness/skills/{name}/disable` | 禁用 |
| DELETE | `/api/harness/skills/{name}` | 删除（**只对用户目录生效**，项目级/内置报错） |

排障顺序：

1. 技能是否在期望的根目录、是否 `skills/<name>/SKILL.md`（或根下 `<name>.md`）。
2. frontmatter 是否扁平、`name` 是否 kebab-case、`description` 是否为空（为空则取正文首个非标题行，仍为空会被跳过）。
3. 是否被禁用（`enabled:false`，看 `/api/harness/skills` 或删掉 `data/skills.properties` 对应行）。
4. `cwd` 是否与预期一致：相对 `./skills` 依赖进程工作目录。
5. 改 `harness.skills.*` 配置后要重启进程（Bean 启动时装配）；**技能文件本身增删改不需要重启**（每次请求重新扫描），只需重新让模型加载该技能。
6. 桌面端入口：设置 → 技能。

## 7. 插件运维

- 放 JAR 到插件目录 → 重启（`harness.plugins.auto-load` 默认 true，启动时自动 install+activate；状态为 DISABLED 的不会被复活）。
- 或 REST：`POST /api/harness/plugins/install`（登记）→ `POST /api/harness/plugins/activate`；替换 JAR 后 `POST /api/harness/plugins/{id}/reload`。
- 列状态：`GET /api/harness/plugins`（status/runtimeState）、`GET /api/harness/plugins/{id}/status`、`GET /api/harness/plugins/catalog`。
- 插件启动失败**只 `log.warn`，不阻断宿主启动**——插件"消失"先查日志和 status，而不是怀疑进程。

```powershell
Invoke-RestMethod "$base/api/harness/plugins" -Headers $h | Select-Object -ExpandProperty data
```

## 8. 常见故障

| 现象 | 排查与处理 |
| --- | --- |
| `Port 8090 was already in use` | `Get-NetTCPConnection -LocalPort 8090 -State Listen \| Select-Object OwningProcess` → `Stop-Process -Id <pid>`；或换 `--server.port=` |
| `Database may be already in use ... jnf-harness.mv.db` | 同一 `./data` 已有实例；停掉旧 java 进程，或换 `SPRING_DATASOURCE_URL`/工作目录 |
| 所有接口 401 | 客户端没带 `X-API-Key`/Bearer；或 `HARNESS_API_KEYS` 里没有该值。想全放行就清空该配置 |
| 误以为已鉴权但谁都能调 | `harness.auth.api-keys` 为空时是全放行；生产必须设置 |
| 模型调用 401/超时/连不上 | 查 `harness.llm.default.base-url`（默认 `http://127.0.0.1:8777/v1`）、key、model；用 `settings/models/test` 验证；必要时设 `HTTPS_PROXY`/`HTTP_PROXY` |
| 改配置不生效 | `application.yml` 里没有的键要在命令行/环境变量给；技能/插件根目录类配置需重启 |
| 老库启动后报表/字段不存在（`Column ... not found`） | 无 Flyway/Liquibase；`schema.sql` 只有 `CREATE ... IF NOT EXISTS`，列级自愈只有 `harness_agent_profile` 的 3 条 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`。给已有表加列必须补 ALTER（由维护者改 `schema.sql`），否则删开发库重建（丢数据） |
| 插件不加载 | 看插件目录解析（`harness.plugins.dir` → jar 同级 `plugins/` → `./plugins`）；`GET /api/harness/plugins/catalog` 是否扫到；status 是否 FAILED；日志 warn |
| 技能不出现 | 见第 6 节 |
| `java` 版本不符 | 需要 JDK 17+（README 与桌面壳都要求 major ≥ 17） |

## 9. Docker / 分发包

```powershell
docker build -t lasso-harness .
docker run --rm -p 8090:8090 -v "${PWD}\data:/app/data" lasso-harness
```

Dockerfile 内：构建 `mvn -q -B clean package -DskipTests`，运行镜像 `eclipse-temurin:17-jre-alpine`，工作目录 `/app`，数据卷 `/app/data`，`EXPOSE 8090`，带 `--spring.profiles.active=standalone`。

本地多 jar：`node scripts/package-local.mjs` 后进 `dist/jnf-harness-local/`，用 `start.bat`（Windows）或 `start.sh`，插件放 `plugins/`，增删 JAR 后重启生效。

## 10. 一页速查

```powershell
mvn clean package -DskipTests
java -jar jnf-harness-app\target\jnf-harness-app.jar --server.port=8090
Invoke-RestMethod http://localhost:8090/actuator/health
$base="http://localhost:8090"; $h=@{ "X-API-Key"="<key>" }
Invoke-RestMethod "$base/api/harness/skills?cwd=$PWD" -Headers $h
Invoke-RestMethod "$base/api/harness/plugins" -Headers $h
Invoke-RestMethod "$base/api/harness/settings/models" -Headers $h
```
