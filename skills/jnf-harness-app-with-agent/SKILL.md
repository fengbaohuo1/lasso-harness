---
name: jnf-harness-app-with-agent
description: 把一句话业务需求交付成「应用为主体 + 内嵌 AI 助手」：独立 Spring Boot 应用（REST + 自绘前端）+ AI 面板代理 /api/agent/stream + Java 插件桥接应用 API；含设计稿、全链路验证与交付清单。
when_to_use: 当用户要一个完整业务应用/平台/系统并希望内置 AI 助手、已有应用要接入 jnf-harness Agent、或要「应用 + 插件」双轨交付时；只做宿主插件界面时改用 jnf-harness-demo-app。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: app
---

# 应用为主体 + 内嵌 AI 助手

用户要的是**一个能点的业务应用**，AI 只是嵌在应用里的助手。界面由应用自己画，数据由应用的 REST 提供，AI 能力靠插件桥到 harness Agent。
产出四件套：**应用地址 + 插件（ACTIVE）+ 每个工具实测记录 + README/体验流程**。

## 0. 两种路径（先问清）

**第一问（必问，二选一）**：给**现有应用**加 AI，还是做**新项目**？

| 路径 | 现状 | 你做什么 |
| --- | --- | --- |
| A 已有应用 | 应用能跑、有 REST 或数据 | 只写 `xxx-plugin`：把应用 API 注册成工具；可选给应用加轻量 AI 入口 |
| B 新项目 | 从零开始 | 写 `xxx-app`（REST + 前端 + AI 面板代理）+ `xxx-plugin`（bridge），一套交付 |

**第二问（仅新项目，三选一，必须用这套措辞）**：
1. **应用 + 插件（AI 可调用）** —— 标准玩法：应用自己画界面，插件把应用 API 注册成 Agent 工具。
2. **仅独立应用（不接 AI）** —— 只生成 `xxx-app`，不进插件列表，AI 调不到；交付时明说「与 harness 无关」。
3. **先要设计稿 + 原型** —— 不写代码，先出实体/工具/页面/预置数据，确认后再开发。

用户说「help / 能做什么 / 没想好」时先按 `references/case-library.md` 的「帮助菜单」回复（一句话能力 + 分类菜单 + 引导），不要直接抛二选一。

**可默认、不必问**：端口 18081 起顺延（底座固定 8090）；内存预置数据（重启即还原）；原生 HTML/CSS/JS 单页；包名 `com.jnf.<domain>`。

## 1. 交付定义（缺一不算交付）

- **应用地址**：交付前刚探活过（HTTP 200），打开就是可用产品；
- **插件**：`plugin.yaml` + SPI + `AbstractHarnessPlugin`，状态 ACTIVE，`/contributions` 能看到全部工具；
- **每个工具至少一条自然语言实测**：`step_break.toolName` 正确、`tool_result` 非空、回答数字与工具返回一致；
- **README + 体验流程 + 生命周期说明**（模板见 `references/delivery-template.md`）；
- **设计语言卡**（主色/材质/形状母题/标志性元素/气质词）随 README 交付。

## 2. 阶段 0：澄清三形态

1. 第一问定路径；第二问定形态；
2. 走路径 A 前先**亲自读项目**：README、`pom.xml`、Controller/Service/领域模型，一句话说出项目在做什么、核心实体、已有 API，再给 3~6 个 AI 扩展场景让用户点选（每个场景写：AI 帮用户做什么、建议拆成哪些工具、价值是什么）；
3. 走路径 B 且用户没想法 → 从 `references/case-library.md` 抽 3~5 个跨分类案例（原话术展示，附「AI 亮点」），末尾留「也可以直接描述任意应用」；
4. 一次问完运行环境（本地/服务器、JDK 17+、Maven、底座是否已在 8090 跑）。

## 3. 阶段 1：环境

```powershell
node <skill-path>\scripts\check_env.mjs        # 查 Node / Java 17+ / Maven
```

- 缺 JDK 17 / Maven：先装再继续（Windows 用 `winget install EclipseAdoptium.Temurin.17.JDK` / `winget install Apache.Maven`）；
- 若在沙箱/受限代理环境，**先读 `references/runtime-pitfalls.md`**（代理导致本机 curl 502、进程被回收两个坑必踩）。

## 4. 阶段 2：启动底座并确认模型

```powershell
node <skill-path>\scripts\start_harness.mjs --harness <workspace>\jnf-harness   # 探活已有实例，未起则后台拉起（jar 缺失时只提示构建命令，不自动构建）
# 等价的显式启动（推荐每次都给端口）
java -jar jnf-harness\jnf-harness-app\target\jnf-harness-app.jar --server.port=8090
```

- 控制台 `http://127.0.0.1:8090/`；**未配置模型时对话必报错**，先到「设置 → 模型」添加渠道（baseUrl/model/apiKey）并点测试；
- 底座鉴权：`harness.auth.api-keys` 为空时放行；设了就必须带 `X-API-Key`；
- 探活：`Invoke-RestMethod http://127.0.0.1:8090/actuator/health` → `{"status":"UP"}`。

## 5. 阶段 3：设计稿（先确认，后写码）

拿到需求**禁止直接写 Java**。先出四块，紧凑发给用户，用选择框确认「开工 / 要调整」：

| 块 | 要求 |
| --- | --- |
| 实体表 | 3~5 个实体，每个 ≥6 个有意义字段（名称/状态/数值/时间/负责人…），够渲染真实页面 |
| 工具表 | 3~6 个：`name`、入参 schema、返回 JSON 结构、`description`（何时必须调、何时不要调、返回什么）、读/写 |
| 页面区块 | 顶栏 + 主区（看板/列表/详情/表单）+ AI 侧栏；写清每个区块用哪些字段 |
| 预置数据 | 每实体 5~8 条，状态机完整、数据之间有故事、中文业务文案、数字可加总 |
| 设计语言卡 | 按 `references/ui-design-guide.md` 定 tokens 与标志性母题；同类案例至少两项不同 |

设计稿确认点必须等用户回话；用户明确说「直接做」才可跳过。

## 6. 阶段 4：开发

### 6.1 应用 REST（`xxx-app`）

- 至少 `GET /api/<domain>/<entity>`（列表，支持分页）、`GET .../{id}`（详情）、`POST ...`（创建，写操作）、`GET .../summary`（汇总，前端与 AI 同源）；
- 统一信封 `{code, info, data}`，`00000` 成功；错误码 40000/50000，**HTTP 仍 200**；
- 数据用内存 `Map` + 启动时种子数据（重启即还原，演示够用）；写操作要真实改变汇总，验证「联动」；
- 应用侧保留 `serviceToken` 校验给插件调用（只对插件路径生效），详见 `references/app-plugin-bridge.md`。

### 6.2 前端

- 原生 HTML/CSS/JS 单页，tokens 全部 CSS 变量；先读 `references/ui-design-guide.md` 并过「无 AI 味清单」；
- 空态/错误态/加载态齐全；`[hidden]{display:none!important}` 全局兜底（否则弹层关不掉）。

### 6.3 AI 面板（应用内）

- 右下角浮动按钮 + 侧滑面板；浏览器只调**应用自己的** `/api/assistant/stream`，**绝不直连 8090、绝不在前端放 API Key**；
- 服务端代理注入 harness API Key、转发 body、逐字节透传 SSE；
- 完整实现（SSE 帧解析、chunk 累积、工具轨迹去重、markdown 渲染、AbortController）见 `references/ai-panel-guide.md`，直接抄。

### 6.4 插件 bridge（`xxx-plugin`）

- 继承 `AbstractHarnessPlugin`，`tools()` 返回工具；`configure(ctx)` 先 `super.configure(ctx)`，再注册系统提示词（写边界：涉及真实数据必须查证、禁止编造）；
- 工具只调应用 REST（超时 3s、异常分类、脱敏），不直连数据库；
- 应用地址从插件配置读（`getConfig("baseUrl", "http://127.0.0.1:18081")`），写死只能演示；
- 工具名带业务前缀；默认暴露为 `plugin__<pluginId>__<tool>`；
- 打包：`mvn -o -B -DskipTests package`，JAR 内必须有 `META-INF/plugin.yaml` 与 SPI 文件。骨架细节读 `jnf-harness-plugin-dev`；
- 最小可运行骨架：`<skill-path>/templates/app-plugin/`（`sample-app` 应用 + `sample-plugin` 插件多模块，直接改造成业务模块）。

## 7. 阶段 5：安装与全链路验证

```powershell
# 1) 构建应用与插件（应用 JAR + 插件 JAR）
mvn -o -B -DskipTests package        # 在各自模块目录

# 2) 起应用（显式端口，别省略 --server.port）
java -jar <app>\target\<app>.jar --server.port=18081

# 3) 插件放目录（宿主 jar 同级 plugins/）→ 启动自动装载；或 REST 登记
node <skill-path>\scripts\install_plugin.mjs --base http://127.0.0.1:8090 --jar <plugin>.jar --plugin-id <id> --version 1.0.0 --entrypoint <主类全名>

# 4) 看状态与注册面
Invoke-RestMethod http://127.0.0.1:8090/api/harness/plugins
Invoke-RestMethod http://127.0.0.1:8090/api/harness/plugins/<id>/contributions
```

**逐工具实测（不是只跑一条 happy path）**：

```powershell
node <skill-path>\scripts\agent_stream.mjs --base http://127.0.0.1:8090 --agent app-demo "帮我查一下 XX 的汇总"
```

每条都要人工核对四件事：`step_break` 里 `toolName` 是否预期工具；`tool_result` 是否非空；回答里的数字是否等于工具返回；写操作是否先复述再执行。

同时验证：
- **算术一致**：汇总 = 明细逐条加总（自己算一遍，别信页面）；
- **写操作联动**：POST 真实数据 → 再查汇总/列表确认变化 → 重启应用还原种子数据；
- **AI 面板代理**：`POST /api/assistant/stream` 流式非空，浏览器里能看到逐字输出与工具轨迹；
- **UI 真浏览器**：每个区块、弹层、表单、AI 面板走一遍，无 JS 报错；curl 测不出「弹层关不掉」。

## 8. 阶段 6：交付文档与验收闭环

1. 按 `references/delivery-template.md` 写 README（使用说明/体验流程/工具清单/数据说明/构建启动/坑位）；
2. 过 `references/delivery-checklist.md`：自动项 + 手工项逐条打勾；
3. 交付时给：探活后的应用地址、插件与工具清单、体验流程说明（每条示例问题注明触发哪个工具）、**服务生命周期说明**（跑在哪、进程可能被回收、重启命令、内存数据重启还原）；
4. 交付后**主动索要验收反馈**（「打开地址过一遍体验流程，要调的告诉我」），用户确认前任务不算完结；
5. 迭代：小改只重跑受影响工具 + 探活；改全局机制（Filter/拦截器/统一异常）要跑回归矩阵（页面流、AI 代理 SSE、Agent 端到端）。

## 9. 硬规则（违反即重做）

1. **AI 面板必须代理 harness stream**：浏览器只访问应用域，API Key 只存在应用服务端；
2. **markdown 必须渲染且先转义**：AI 气泡禁止 `textContent` 裸显，先 `escapeHtml` 再处理标题/加粗/列表/代码块，流式逐 chunk 重渲染；用户气泡用 `textContent`；
3. **工具 `description` 写边界**：何时必须调用（涉及真实数据必须查证）、何时不要调用、返回什么结构；
4. **写操作必须二次确认**：description 声明风险，执行前复述影响，返回「改了什么、旧值→新值」；
5. **应用 API 与工具口径一致**：同一条数据不能页面一个数、工具另一个数；汇总走同一个 service；
6. **禁止把工具卡当平台 UI**：应用自己的业务界面自己画；`plugin-ui.json` 只是宿主里的附加展示，不是本交付的主界面；
7. **先设计稿后写码**：实体/工具/页面/数据四块给用户确认；
8. **数字只来自工具**：AI 回答与前端的数值都必须能追到应用 REST/工具返回，禁止现编；
9. **端口显式**：底座 `--server.port=8090`、应用 `--server.port=18081`（起服务一律带参）；
10. **不写密钥、不写本机绝对路径**：示例用 `<key>` / `<workspace>` / `<skill-path>`。

## 10. Gotchas

- **未配模型**：对话报错先查「设置 → 模型」，不是插件问题；
- **插件改了配置不生效**：`getConfig` 即用即读，但应用地址若在 `configure()` 缓存，需 `reload` 插件；
- **插件端点路径精确匹配**：`/api/harness/plugins/<id>/api/<path>` 的 `<path>` 用**单段**最稳，注册路径首尾 `/` 会被去掉，路径变量不参与匹配；
- **`step_break` 可能重复推送**：展示层按 `callId` 去重，别把一次调用画成两次；
- **SSE 字段是字符串**：`step_break.args`、`tool_result.result` 是 JSON 字符串（要 `JSON.parse`），`tool_result.error` 是布尔，`done.toolCalls` 是数量（数字）；
- **CORS**：底座 `allowedOriginPatterns("*")` 但不带凭据；应用代理走服务端就没 CORS 问题——优先服务端代理；
- **H2 与数据目录**：用默认 `MODE=MySQL` 的 H2 连接串，别自造方言；`./data` 相对进程 cwd，换目录起会像「数据丢了」；
- **Windows/PowerShell 引号**：JSON body 用 `@{} | ConvertTo-Json` 或单引号包 JSON，双引号嵌套会炸；
- **代理导致本机 curl 502**：先 `Get-NetTCPConnection -LocalPort <port> -State Listen` 确认在听，再判断是不是代理。
- 更多实测坑位与排查配方：`references/runtime-pitfalls.md`。

## 11. references 索引（按需读，别一次全读）

| 文件 | 何时读 |
| --- | --- |
| `references/architecture.md` | 要画架构图/给用户解释三层链路、写 README 架构节、定契约时 |
| `references/ai-panel-guide.md` | 写 AI 面板与 `/api/assistant/stream` 代理时（含可直接抄的 SSE 消费与 markdown 渲染） |
| `references/ui-design-guide.md` | 动前端之前：tokens、设计语言卡、无 AI 味清单、布局与空态 |
| `references/app-plugin-bridge.md` | 设计应用 REST 与插件工具映射、写系统提示词边界、做 serviceToken 时 |
| `references/delivery-checklist.md` | 交付前收尾，逐条打勾 |
| `references/runtime-pitfalls.md` | 启动/联调出问题，或进入沙箱/受限代理环境前 |
| `references/case-library.md` | 用户说 help、没想好做什么、要案例或要写新案例 prompt 时 |
| `references/delivery-template.md` | 写 README、体验流程、生命周期说明时 |

## 12. 与相邻技能的边界

- 插件怎么写、`plugin.yaml` 字段、Hook/Endpoint/配置、报错对照 → `jnf-harness-plugin-dev`（本技能不重复）；
- 只要「Agent 为主体、界面由宿主 `plugin-ui.json` 渲染」的演示 → `jnf-harness-demo-app`；
- 底座怎么构建/启动/排障（端口、鉴权、数据目录、模型渠道） → `jnf-harness-ops`；
- 本技能 = **应用自己画界面 + AI 嵌在应用里 + 插件只做桥**。

## 13. 自检清单

- [ ] frontmatter `name` = 目录名；description 有触发场景与产出物
- [ ] 设计稿已确认；预置数据无「示例/测试/A 商品」式占位
- [ ] 应用 REST 与工具口径一致，汇总=明细加总
- [ ] AI 面板走应用代理，前端无 key；markdown 渲染且流式重渲染
- [ ] 插件 ACTIVE、`/contributions` 工具齐全、每个工具至少一条自然语言实测
- [ ] 写操作先确认、有回显、可重启还原
- [ ] UI 真浏览器点过；README/体验流程/生命周期齐备；地址刚探活
