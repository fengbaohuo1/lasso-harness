# jnf-harness

> jnf的自有 Agent Harness。
> 施工计划：`../plan/README.md`（总纲）与 `../plan/第一阶段-复刻dsh-java.md`（逐节明细）。
> 参照实现：`../deepseek-harness-java`（架构与功能对齐对象，代码指代保留其原名以便查证）。

## 当前进度

- 第 0 节 工程骨架与质量门禁：进行中

## 构建

```bash
mvn clean verify        # 含 ArchUnit 分层门禁
```

## 模块

| 模块 | 职责 |
| --- | --- |
| jnf-harness-types | SPI 契约包（插件 SPI + 工具/消息模型），零框架依赖 |
| jnf-harness-domain | 领域层：限界上下文、端口、ReAct 循环、事件溯源 |
| jnf-harness-infrastructure | 端口实现：LLM 网关、MyBatis、Shell、MCP |
| jnf-harness-api | 应用门面契约 + DTO + 统一响应 |
| jnf-harness-case | 用例编排（策略树 factory/node） |
| jnf-harness-trigger | REST/SSE 接入 + 鉴权 |
| jnf-harness-app | Spring Boot 启动器 |
