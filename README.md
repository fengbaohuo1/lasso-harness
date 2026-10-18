# lasso-harness

> lasso 的自有 Agent Harness——DDD 六边形 + ReAct 循环 + 会话事件溯源 + 三通道插件。
> 配套桌面壳：[lasso-harness-desktop](https://github.com/fengbaohuo1/lasso-harness-desktop)。
>
> **命名说明**：仓库对外名称为 `lasso-harness`；Maven 模块、Java 包名（`com.jnf.harness`）与构建产物仍沿用 `jnf-harness-*` 命名，与源码保持一致，下文命令均按实际模块名执行。

## 构建与运行

```bash
mvn clean verify                                      # 构建 + 全部测试（含 ArchUnit 分层门禁）
java -jar jnf-harness-app/target/jnf-harness-app.jar  # 启动（默认 H2 文件库 ./data/）
node scripts/package-local.mjs                        # 本地免 Docker 部署包（dist/，多 jar 布局）
docker build -t lasso-harness .                       # 容器镜像
```

**多 jar 交付（一切皆插件）**：内核 fat jar 不含任何插件；`jnf-harness-app/target/plugins/`
产出官方插件 jar，启动时自动装载（内核 jar 同级 `plugins/` 目录零参数发现，放入/移除 jar 即增删）。
`java -jar` 能跑但无 fs/shell/web 工具属正常——工具在插件 jar 里。

打开 `http://localhost:8090/` 即为 Web 控制台（原生 JS，无构建链）。

> R12 纪律：本文档不写死任何数量指标；模块/源码/测试数量以 `node scripts/stats.mjs` 现算为准。

## 模块

| 模块 | 职责 |
| --- | --- |
| jnf-harness-types | **插件 SDK**（SPI 契约 + 工具/消息模型 + 宿主服务接口），零框架依赖；第三方插件只依赖它（provided） |
| jnf-harness-domain | 领域层：限界上下文、端口、ReAct 循环、事件溯源、治理、插件宿主 |
| jnf-harness-infrastructure | 端口实现：LLM 三协议网关、MyBatis/JdbcTemplate、Shell、MCP/Node sidecar、插件类加载 |
| jnf-harness-api | 门面契约 + DTO + 统一响应 |
| jnf-harness-case | 用例编排（策略树 factory/node） |
| jnf-harness-trigger | REST/SSE 接入 + 鉴权 |
| jnf-harness-plugin-fs | **官方插件 jar**：fs_read/fs_write/fs_search/fs_grep/fs_read_image/str_replace_editor |
| jnf-harness-plugin-shell | **官方插件 jar**：shell_execute（执行通道经宿主 ShellService，命令守卫内建） |
| jnf-harness-plugin-web | **官方插件 jar**：web_fetch/web_search（出网经宿主 WebService） |
| jnf-harness-plugin-essentials | **官方插件 jar**：todo_write/exit_plan_mode/lsp_query/session_search（内核瘦身外移） |
| jnf-harness-sample-plugin | 示例插件 JAR（前后端全栈模板：五注册口 + UI 清单 + 插件端点） |
| jnf-harness-app | Spring Boot 启动器 + Web 控制台 + schema + 组合根 |

## 能力面

- **Agent**：ReAct 循环（工具续步/步数上限/受控续写/协作取消）、上下文预算与压缩、token 计量
- **工具**：13 个工具族（fs/shell/web/mcp/plugin/skill/jobs/terminal/schedule/goal/lsp/session/extension）
- **插件三通道**：Java SPI（隔离 ClassLoader、事务边界逆序回收、热重载）、Node Bridge（JSON-RPC sidecar）、MCP（stdio）
- **一切皆插件**：内核 jar + 插件 SDK jar（jnf-harness-types）+ N 个插件 jar；官方工具族（fs/shell/web）即插件 jar；
  `plugins/` 目录自动装载（重启即用）+ classpath ServiceLoader 发现（开发/测试）+ `plugin__` 限定名/全局名双命名空间 + sdkVersion 握手
- **前后端插件统筹**：插件随包携带前端贡献清单 `META-INF/plugin-ui.json`（工具卡渲染器 + **独立配置页面** + 动作按钮 + **斜杠命令 slashCommands** + **@ 提及源 mentionProviders**）；
  `registerEndpoint` 注册插件 REST 端点（`/api/harness/plugins/{id}/api/**`，统一鉴权/信封，随插件停用回收）；
  UI-only 插件（仅清单无后端入口）支持；桌面端按清单运行时渲染，改清单免重编壳
- **插件管理**：目录发现（catalog）、install/activate/disable/reload/uninstall、配置 KV 持久化、启动自动恢复（ACTIVE 重激活）
- **治理**：运行期审批（挂起-裁决，审批历史落表）、沙箱三档、命令守卫、任务五子域状态机（防绕过）
- **会话**：事件溯源（sealed 事件 + WAL + 投影 + 压缩 + 回放/对账）
- **模型**：渠道管理（CRUD/唯一激活/删除保护/测试连接/厂商预设/模型发现）+ openai/anthropic/ollama 三协议 + turn 级热切换（DB 优先/文件兜底）
- **智能体画像**：per-agent 配置（步数/上下文预算/工具画像白名单/提示词补丁），消息级 `profile` 提示——token 开销按场景裁
- **开放协议**：三张 Agent Card、A2A JSON-RPC、数字人注册表、协作房间（事件续传）
- **周边**：goal/plan/todo、jobs/schedule（持久化）、workflow、terminal、扩展管理

## 配置

| 配置 | 说明 |
| --- | --- |
| `harness.auth.api-keys` | API Key（逗号分隔；空=放行，生产必配） |
| `harness.llm.default.*` | 默认模型渠道（base-url/api-key 引用/model） |
| `harness.llm.seed-default-channel` | 首启渠道表为空时 seed default 渠道（默认 true） |
| `harness.plugins.dir` | 插件目录（空=自动：内核 jar 同级 `plugins/` → `./plugins`） |
| `harness.plugins.auto-load` | 插件目录自动装载：新 jar 自动登记+激活，停用的不复活（默认 true） |
| `harness.events.dir` | 会话事件/元数据目录（默认 `./data/events`） |
| `harness.capabilities.*` | 能力开关（task/goal/a2a/digital-human/collaboration/jobs/schedule/terminal/extension，默认全开）：关闭=工具不注册、专属服务与 REST 不装配 |
| `harness.approval.mode` | `REQUEST_APPROVAL`（默认）/ `AUTO_APPROVE` |
| `harness.sandbox.mode` | `READ_ONLY` / `WORKSPACE_WRITE`（默认）/ `DANGER_FULL_ACCESS` |
| `spring.datasource.url` | 默认 H2 文件库；MySQL 用环境变量覆盖 |

## 已知边界（生产化清单）

- 单实例假设（Agent 状态在内存；A2A 任务态重启即失）
- `shell_execute` 靠命令守卫 + 沙箱，无内核级隔离（容器化部署兜底）
- schedule 无定时触发引擎；lsp/coderuntime/e2b 为存根接口
- 密钥零硬编码（R9）：凭据一律引用——`env:` 环境变量或 `secret:` 本地密钥库（data/secrets.json，Base64 混淆，非 HSM 级）；API 只回 hasApiKey
