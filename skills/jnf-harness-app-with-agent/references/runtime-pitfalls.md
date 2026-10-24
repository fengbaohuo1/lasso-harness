# 运行时坑位与排查配方（本底座实测）

按「现象 → 原因 → 处理」查。命令以 Windows PowerShell 为主，给 bash 等价。

## 1. 启动与端口

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Port 8090 was already in use` / 应用起不来 | 端口占用（旧进程、桌面壳、另一个实例） | `Get-NetTCPConnection -LocalPort 8090 -State Listen \| Select-Object OwningProcess` → `Stop-Process -Id <pid>`；或换 `--server.port=` |
| 应用绑到了随机端口 | 沙箱/环境注入 `SERVER_PORT`，Spring relaxed binding 优先级高于 yml | **命令行显式**：`java -jar app.jar --server.port=18081`；启动后核对实际监听 |
| 换个目录启动就「数据丢了」 | `./data` 相对**进程 cwd** | 固定启动目录；或在 README 写明启动目录 |
| `Database may be already in use ... jnf-harness.mv.db` | 同一 `./data` 两个实例 | 停旧进程，或 `SPRING_DATASOURCE_URL` 换库文件 |
| 服务「自己死了」 | 会话/沙箱回收了子进程 | 用受管后台任务启动；每次交付前先探活，没了就重拉 |

```powershell
# 端口探活（真探活，不猜）
Test-NetConnection 127.0.0.1 -Port 8090
Invoke-RestMethod http://127.0.0.1:8090/actuator/health
```

## 2. H2 与数据库

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动时报 SQL 语法/方言错误 | 自造 H2 连接串，丢了 MySQL 兼容模式 | 用默认连接串形态：`jdbc:h2:file:./data/jnf-harness;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE`，不要去掉 `MODE=MySQL` |
| 中文变乱码 | SQL 脚本/种子数据编码未声明 | 初始化显式 `spring.sql.init.encoding: UTF-8`；Java 源码与资源统一 UTF-8 |
| `Column ... not found` | 无 Flyway/Liquibase，`schema.sql` 只有 `CREATE ... IF NOT EXISTS` | 给已有表加列必须补 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`；或停进程删 `data/jnf-harness.mv.db` 重建（丢数据） |
| 想重置演示状态 | 持久化在文件库 | 停进程 → 删/移 `./data/jnf-harness.mv.db` → 重启 |

> 应用侧（`xxx-app`）用内存 Map 时没有这个坑：重启即还原，写操作验收完重启即可。

## 3. 鉴权（API Key）

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 所有接口 401，body `{"error":"Unauthorized",...}` | `harness.auth.api-keys` 已配置，但请求没带 key | 带 `X-API-Key: <key>` 或 `Authorization: Bearer <key>` |
| 以为配了 key 但谁都能调 | `harness.auth.api-keys` 为空 = **全放行** | 生产必须设置；交付时说明当前鉴权状态 |
| 401 的返回不是 `{code,info,data}` | 拦截器直接写 401 + 固定 JSON | 客户端要同时处理「信封」与「401 裸 JSON」两种 |
| 桌面壳启动后本地调 401 | 壳会随机生成 key 并通过 `--harness.auth.api-keys` 传入 | 从壳的运行时文件/日志取 key，或用 `java -jar` 自启 |

免鉴权前缀（拦截器白名单）：`/actuator`、`/.well-known`、`/`、`/index*`、`/app.*`、`/lib/`、`/favicon`，以及所有 `OPTIONS` 预检。

## 4. CORS 与浏览器直连

- 底座 `WebMvcConfig`：`allowedOriginPatterns("*")`、允许常用方法、`allowedHeaders("*")`、`exposedHeaders("*")`、**`allowCredentials(false)`**；
- 带 Cookie/凭据的跨域请求会失败——本架构不需要浏览器直连底座，**一律走应用服务端代理**；
- 浏览器直连 8090 还会把 API Key 暴露给页面，属于安全红线；
- 代理方案下 CORS 不参与（同源），只需处理 SSE 的缓冲：响应加 `Cache-Control: no-cache`、`X-Accel-Buffering: no`，别让网关攒帧。

## 5. 插件装载与激活

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `catalog` 为空 | jar 不在宿主解析的插件目录 | 解析顺序：`harness.plugins.dir` → **内核 jar 同级 `plugins/`** → 进程 cwd `./plugins`；命令行启动即 `jnf-harness-app/target/plugins/` |
| install 报「制品不存在」 | `sourcePath` 写错 | 用**绝对路径**，或相对插件目录的文件名；install 的 `entrypoint` 是**主类全名**（与 `plugin.yaml` 的 `entrypoint` 同义，别填 jar 名） |
| 启动后插件已 ACTIVE | `harness.plugins.auto-load` 默认 true，启动即 install+activate | 不必再手动 install；`activate` 幂等，脚本可无脑重试 |
| activate 失败 | `sdkVersion` 高于宿主、`entrypoint` 类不存在、缺 SPI 文件 | 看 `/api/harness/plugins/{id}/status` 与日志 warn；插件失败**不阻断宿主启动** |
| 覆盖 jar 后行为没变 | 仍用旧类加载器 | `POST /api/harness/plugins/{id}/reload` |
| 改插件配置不生效 | 值在 `configure()` 里缓存了 | 改完 `reload`；代码里尽量「即用即读」`getConfig` |
| 工具名冲突 | 用了 `toolNamespace: global` | 全局唯一；否则保持默认 `plugin__<id>__<tool>` |

## 6. 插件 REST 端点（路径匹配）

- 挂载：`/api/harness/plugins/{pluginId}/api/{path}`；
- 注册路径**规范化**：去掉首尾 `/`；含 `..`、`//`、`\` 直接拒绝；
- 查找是 **pluginId + method + path 精确匹配**：`GET stats` 与 `GET stats/detail` 是两条不同端点；**路径变量不参与匹配**；
- **单段路径最稳**（如 `selftest`、`stats`、`health`），多段虽可注册但容易被前端拼接错误；
- 查询串可传：`/api/harness/plugins/<id>/api/list?page=1`，处理器从 `PluginHttpRequest.query()` 取；
- 未命中返回 `40000`：`插件端点不存在: POST /x`；方法不符返回 `40000`：`方法不允许: GET /x`；
- 端点注册随插件停用/卸载回收，停用后调用即 404 业务码。

## 7. SSE 与 Agent 运行态

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 面板工具卡出现两次 | `step_break` 会重复推送 | 按 `callId` 去重；单次执行只有一条 `tool_result` |
| `args`/`result` 是字符串不是对象 | 底座透传的是 JSON 字符串 | 前端 `JSON.parse`，失败按纯文本展示 |
| 后续请求报「正在执行上一条消息」 | 上次流式被强断，Agent 卡 RUNNING，无 stop 端点 | 重启底座；自动化不要强杀流式请求进程 |
| 流长时间无输出 | 模型慢/未配置模型 | 15s 一条 `:keepalive` 注释帧属正常；面板设 120s 空闲超时 |
| 流式请求参数错却拿到 200 | 握手后无法改状态码，底座用 SSE `error{code:"INVALID_ARGUMENT"}` 返回 | 前端按 `error` 事件处理，别只看 HTTP 状态 |
| 响应结束但内容不全 | 只看 `chunk` 累积，丢帧/重复 | 以 `done.text` 为准 |
| 图片附件被拒 | 模型未勾选图片输入 | 底座返回 `40000`「当前模型不支持图片输入」；文本场景不要传 `attachments` |

## 8. 模型与对话

- 未配置模型（或 key 错）：`/api/agent/stream` 直接返回 `error` 事件，面板显示「AI 服务未就绪」；先到「设置 → 模型」添加渠道并点测试，再试；
- 渠道配置：`POST /api/harness/settings/models`，`apiKey` 语义为「缺省=不改 / 空串=清除 / 非空=覆盖」；`apiKeyRef` 支持 `env:<NAME>` / `secret:<name>`；
- `agentId` 无需预置：底座按需创建，任意 `agentId`（如 `app-assistant`）可直接用于端到端测试；同一应用固定一个 id，方便连续对话；
- `cwd` 会影响技能发现与工具相对路径，代理时用**服务端登记的目录**，不要拼用户输入。

## 9. 网络与代理（沙箱/公司网必踩）

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 本机 `curl 127.0.0.1:8090` 返回 502/000 | 环境 `HTTP_PROXY` 把本机请求转给了代理 | `curl.exe --noproxy "*"`；PowerShell 7 用 `-NoProxy`；或 `$env:NO_PROXY='127.0.0.1,localhost'` |
| 明明 502 但服务在跑 | 同上，先确认监听再判断 | `Get-NetTCPConnection -LocalPort <port> -State Listen` |
| 应用代理连不上底座 | 代理继承环境变量（JDK HttpClient 默认读 `HTTP_PROXY`） | 启动应用时清 `HTTP_PROXY/HTTPS_PROXY`，或代码里用直连 `HttpClient`（`ProxySelector.of(null)` / 不设代理） |
| Maven 构建失败（拉不到依赖） | 离线/镜像问题 | 优先离线：先 `mvn -N install`、`mvn -pl jnf-harness-types install -DskipTests`，再在插件模块 `mvn -o -B -DskipTests package` |

## 10. Windows / PowerShell 专用

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| JSON body 报 400/JSON 解析失败 | 双引号被 PowerShell 吃掉 | 用 `@{...} \| ConvertTo-Json`，或单引号包整段 JSON；`curl.exe` 转义 `\"` |
| `curl` 行为奇怪 | PowerShell 里 `curl` 是 `Invoke-WebRequest` 别名 | 显式写 `curl.exe` |
| 找不到命令 | Node/Java/Maven 不在 PATH | `node -v`、`java -version`、`mvn -v` 自查；用 `Get-Command` 定位 |
| 中文乱码 | 控制台编码 | `chcp 65001` / `[Console]::OutputEncoding=[Text.Encoding]::UTF8`；文件统一 UTF-8 无 BOM |
| 后台进程被回收 | 会话结束回收子进程 | 受管后台任务启动；或 `Start-Process` 新窗口启动 |

## 11. workspace / cwd 相关

- 底座技能根：`<cwd>/.jnf/skills`（PROJECT）> `harness.skills.roots`（默认 `./skills`）> `~/.jnf/skills`；**相对路径按进程 cwd 解析**，换目录启动就找不到技能；
- 插件数据目录：宿主不提供，插件相对路径以 `ctx.cwd()` 为基准；需要落盘时用配置项给绝对路径（如 `dataDir`）；
- 应用工作目录：决定 `./data`、静态资源、日志位置；交付说明里写清「从哪个目录启动」；
- 桌面壳有独立运行时目录与随机 key（`agent-runtime.json`），排查端口冲突先看它。

## 12. 标准排查顺序（照着走，别乱试）

1. **探活**：底座 8090、应用 18081 各自 `health`/首页是否 200；
2. **看鉴权**：401 → 加 key；200 但 code!=00000 → 看 info；
3. **看插件**：`/api/harness/plugins` 状态 → `/catalog` 是否扫到 → `/contributions` 工具在不在；
4. **绕开模型验工具**：插件 `selftest` 端点 / `curl` 应用 REST；
5. **验 Agent**：`agent_stream.mjs` 看 `step_break`/`tool_result`，确认工具真被调用；
6. **验代理**：`curl -N POST /api/assistant/stream` 是否有 `event: meta/chunk/done`；
7. **验 UI**：真浏览器点，ConSOLE 有没有 JS 报错；
8. 仍不通：清代理环境变量、重启两个进程，再看日志（底座日志只到 stdout，桌面壳写 `agent.log`）。

## 13. 独立产品模式专用坑位

打包成「自带 runtime + 18100/18081 + 独立 data」后新出现的坑（规则见 `references/product-packaging.md`）：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 产品启的是 8090、读到开发底座的数据 | 沿用旧启动命令或环境变量 `SERVER_PORT`/`SPRING_DATASOURCE_URL` 把端口/库带偏 | 启动脚本**显式**传 `--server.port=18100` 与 `--spring.datasource.url=jdbc:h2:file:<product>/data/harness/...`；起后 `Get-NetTCPConnection` 核对端口，看 `/actuator/health` 的进程 PID 是否等于脚本记录 |
| 产品端口冲突（18100 被占 / 应用 18081 被开发期应用占） | 机器上同时跑开发底座、多个产品、上一次没停的进程 | 查占用：`Get-NetTCPConnection -LocalPort 18100,18081 -State Listen \| Select-Object LocalPort,OwningProcess`；多产品按 +10（harness）/ +1（应用）错开；不要 kill 不认识 PID |
| `Database may be already in use ... jnf-harness.mv.db` | 同一 `data/harness` 被两个进程打开（产品副本 + 开发底座共用 data，或旧进程未死） | 一个产品一份 `data/`，绝不与开发仓库共享；先 `stop.ps1` 再起；确认 `Get-Process java` 里只有一个用于该 `data/` 的进程 |
| 产品目录外多出 `~/.jnf/skills`、`~/.<plugin>`、散落 `data/` | 进程 cwd 不是产品根，或插件用相对路径/用户目录写数据 | `Start-Process -WorkingDirectory <product>` / `cd <product>`；插件落盘一律 `getConfig("dataDir")`，启动脚本用 `PUT /api/harness/plugins/{id}/config` 写到 `<product>/data/<plugin>`；验收时检查 `$env:USERPROFILE\.jnf` 无新增 |
| 30MB jar 分发/升级出问题（拷一半、版本混用、sha256 不符） | 手工拷贝中断、覆盖了不同版本、没记录版本 | `runtime/VERSION.txt` 记版本 + sha256，`Get-FileHash` 校验；升级先 `stop.ps1` 再整文件替换（不要边跑边覆盖），换完复验健康/插件 ACTIVE/contributions/应用 AI 流式；回滚要 jar 与 `data/` 成对 |
| `start.ps1` 重复执行后两个实例/端口打架 | 上一实例没停、或健康检查误判（端口在听但服务没起好） | 脚本只按**健康接口**判复用；重置先 `stop.ps1`（校验 PID + 命令行）再 `start.ps1`；连续跑两次应看到第二次 `[SKIP]` 而不是新 PID |
| `start.ps1` 中文乱码 / 报「字符串缺少终止符」 | Windows PowerShell 5.1 按 GBK 读 UTF-8 无 BOM 脚本；或复制脚本时混了全角引号 | 用 `pwsh`（PowerShell 7）执行；5.1 下把文件另存为 UTF-8 with BOM；语法自查：`$errs=$null; [System.Management.Automation.Language.Parser]::ParseFile($p,[ref]$null,[ref]$errs); $errs.Count` 必须为 0 |
| `stop.ps1` 说「无 PID 记录 / 命令行不含产品路径」 | 启动时复用了外部已起进程（无 PID），或 PID 被系统复用 | 这是**保护**不是故障：复用进程需手工按端口/命令行确认后再停；确实归属本产品时可 `-Force`（仅限确认过） |
