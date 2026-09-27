# jnf-harness

> jnf 的自有 Agent Harness——DDD 六边形 + ReAct 循环 + 会话事件溯源 + 三通道插件。
> 施工计划与验收：`../plan/README.md`（总纲）与 `../plan/第一阶段-复刻dsh-java.md`（逐节明细）。
> 参照实现：`../deepseek-harness-java`（架构与功能对齐对象；代码指代保留其原名以便查证）。

## 构建与运行

```bash
mvn clean verify                                      # 构建 + 全部测试（含 ArchUnit 分层门禁）
java -jar jnf-harness-app/target/jnf-harness-app.jar  # 启动（默认 H2 文件库 ./data/）
node scripts/package-local.mjs                        # 本地免 Docker 部署包（dist/）
docker build -t jnf-harness .                         # 容器镜像
```

打开 `http://localhost:8090/` 即为 Web 控制台（原生 JS，无构建链）。

> R12 纪律：本文档不写死任何数量指标；模块/源码/测试数量以 `node scripts/stats.mjs` 现算为准。

## 模块

| 模块 | 职责 |
| --- | --- |
| jnf-harness-types | SPI 契约包（插件 SPI + 工具/消息模型），零框架依赖 |
| jnf-harness-domain | 领域层：限界上下文、端口、ReAct 循环、事件溯源、治理 |
| jnf-harness-infrastructure | 端口实现：LLM 三协议网关、MyBatis/JdbcTemplate、Shell、MCP/Node sidecar |
| jnf-harness-api | 门面契约 + DTO + 统一响应 |
| jnf-harness-case | 用例编排（策略树 factory/node） |
| jnf-harness-trigger | REST/SSE 接入 + 鉴权 |
| jnf-harness-sample-plugin | 示例插件 JAR（五注册口演示） |
| jnf-harness-app | Spring Boot 启动器 + Web 控制台 + schema |

## 能力面

- **Agent**：ReAct 循环（工具续步/步数上限/受控续写/协作取消）、上下文预算与压缩、token 计量
- **工具**：13 个工具族（fs/shell/web/mcp/plugin/skill/jobs/terminal/schedule/goal/lsp/session/extension）
- **插件三通道**：Java SPI（隔离 ClassLoader、事务边界逆序回收、热重载）、Node Bridge（JSON-RPC sidecar）、MCP（stdio）
- **插件管理**：目录发现（catalog）、install/activate/disable/reload/uninstall、配置 KV 持久化、启动自动恢复（ACTIVE 重激活）
- **治理**：运行期审批（挂起-裁决，审批历史落表）、沙箱三档、命令守卫、任务五子域状态机（防绕过）
- **会话**：事件溯源（sealed 事件 + WAL + 投影 + 压缩 + 回放/对账）
- **模型**：渠道管理（CRUD/唯一激活/删除保护/测试连接/厂商预设/模型发现）+ openai/anthropic/ollama 三协议 + turn 级热切换（DB 优先/文件兜底）
- **开放协议**：三张 Agent Card、A2A JSON-RPC、数字人注册表、协作房间（事件续传）
- **周边**：goal/plan/todo、jobs/schedule（持久化）、workflow、terminal、扩展管理

## 配置

| 配置 | 说明 |
| --- | --- |
| `harness.auth.api-keys` | API Key（逗号分隔；空=放行，生产必配） |
| `harness.llm.default.*` | 默认模型渠道（base-url/api-key 引用/model） |
| `harness.llm.seed-default-channel` | 首启渠道表为空时 seed default 渠道（默认 true） |
| `harness.plugins.dir` | 插件目录（catalog 扫描根，默认 `./plugins`） |
| `harness.approval.mode` | `REQUEST_APPROVAL`（默认）/ `AUTO_APPROVE` |
| `harness.sandbox.mode` | `READ_ONLY` / `WORKSPACE_WRITE`（默认）/ `DANGER_FULL_ACCESS` |
| `spring.datasource.url` | 默认 H2 文件库；MySQL 用环境变量覆盖 |

## 已知边界（生产化清单）

- 单实例假设（Agent 状态在内存；A2A 任务态重启即失）
- `shell_execute` 靠命令守卫 + 沙箱，无内核级隔离（容器化部署兜底）
- schedule 无定时触发引擎；lsp/coderuntime/e2b 为存根接口
- 密钥零硬编码（R9）：凭据一律引用——`env:` 环境变量或 `secret:` 本地密钥库（data/secrets.json，Base64 混淆，非 HSM 级）；API 只回 hasApiKey
