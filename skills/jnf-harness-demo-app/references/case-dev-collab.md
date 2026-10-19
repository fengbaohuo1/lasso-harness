# 案例 P31 · 迭代协作工作台（`demo-dev-collab`）

> 需求原话（插件版）：
> 「做一个研发迭代工作台：看当前迭代的目标/进度、需求列表与详情、风险清单；AI 助手能汇总迭代进展、找出延期风险、
> 生成本周周报初稿，并在我确认后新建需求。以 jnf-harness 插件形式提供。」

演示价值：需求看板 + 风险判断 + **文本生成（周报）**，最贴近“团队日常让 AI 干活”的用法。

## 1. 实体与预置数据

迭代 `sprint`（1 条）

| id | name | goal | startDate | endDate | progress |
| --- | --- | --- | --- | --- | --- |
| sprint-42 | 2026-10 迭代 | 完成支付重构与对账优化 | 2026-10-12 | 2026-10-25 | 62% |

需求 `requirements`（8 条）

| id | title | status | owner | priority | storyPoints | updatedAt |
| --- | --- | --- | --- | --- | --- | --- |
| req-101 | 支付渠道路由重构 | 开发中 | 陈默 | P0 | 8 | 2026-10-19 10:12 |
| req-102 | 对账差异自动告警 | 开发中 | 林悦 | P1 | 5 | 2026-10-19 09:40 |
| req-103 | 退款失败重试 | 待评审 | 周航 | P1 | 3 | 2026-10-18 17:22 |
| req-104 | 支付结果页改版 | 测试中 | 陈默 | P2 | 3 | 2026-10-19 08:05 |
| req-105 | 渠道限额配置化 | 已完成 | 林悦 | P1 | 5 | 2026-10-17 18:30 |
| req-106 | 对账任务拆分 | 已完成 | 周航 | P2 | 2 | 2026-10-17 16:11 |
| req-107 | 支付埋点补全 | 阻塞 | 陈默 | P2 | 2 | 2026-10-16 14:02 |
| req-108 | 灰度开关收敛 | 待评审 | 林悦 | P3 | 1 | 2026-10-16 11:20 |

风险 `risks`（4 条，直接预置，不靠模型推演）

| id | title | level | reason | owner | suggestion |
| --- | --- | --- | --- | --- | --- |
| risk-1 | 支付渠道路由重构延期 | 高 | 依赖外部渠道联调，未排期 | 陈默 | 本周内确认联调窗口，否则拆分上线 |
| risk-2 | 支付埋点补全阻塞 | 中 | 等待数据组提供埋点方案 | 陈默 | 升级到周会，指定方案负责人 |
| risk-3 | 对账差异告警测试覆盖不足 | 中 | 仅 2 条用例，缺异常分支 | 林悦 | 补 4 条异常用例后再进测试 |
| risk-4 | 退款重试需求未评审 | 低 | 影响 P1 排期 | 周航 | 周三前完成评审 |

周报骨架 `weeklyTemplate`（插件内置，`weekly_draft` 用它拼装，数据全部来自上面实体）

```
## 本周进展
- 已完成：
- 进行中：
## 风险与求助
## 下周计划
```

## 2. 工具契约（读 4 + 写 1 + 生成 1）

| name | 入参 | 返回 | description 要点 |
| --- | --- | --- | --- |
| `sprint_overview` | `sprintId`(可选，默认当前) | `{"sprint":{id,name,goal,startDate,endDate,progress},"stats":{total,done,doing,blocked,pointsDone}}` | 问“迭代进展/整体情况”时先调 |
| `requirement_query` | `status`(可选), `owner`(可选) | `{"total":8,"items":[{id,title,status,owner,priority,storyPoints,updatedAt}]}` | 问“需求列表/谁在做什么/哪些没完成”时调 |
| `requirement_detail` | `requirementId`(必填) | `{"requirement":{...},"history":[{at,from,to,actor}]}` | 问“这条需求细节/状态怎么变的”时调 |
| `risk_list` | `sprintId`(可选) | `{"total":4,"risks":[{id,title,level,reason,owner,suggestion}]}` | 问“有什么风险/会延期吗”时调；只报预置风险，不自行发明 |
| `weekly_draft` | `sprintId`(可选) | `{"markdown":"## 本周进展\n..."}` | 问“写周报/汇总本周”时调；内容只汇总工具数据 |
| `requirement_create` | `title`(必填), `owner`(必填), `priority`(enum P0~P3) | `{"ok":true,"id":"req-109","title","owner","priority","status":"待评审"}` | **写操作**：用户确认后调用；返回新需求 id 与初始状态 |

错误语义：未知需求 → `fail("需求不存在: <id>","REQ_NOT_FOUND")`；创建缺 owner → `fail("owner 不能为空","INVALID_ARGUMENT")`。

## 3. UI 映射

- `toolRenderers.sprint_overview`：kv（goal/progress）+ kv（`result.stats`）；
- `toolRenderers.requirement_query`：table（id/title/status/owner/priority/storyPoints）；
- `toolRenderers.risk_list`：table（title/level/owner/reason/suggestion）；
- `toolRenderers.weekly_draft`：code（`result.markdown`，language `markdown`）；
- `toolRenderers.requirement_create`：kv（id/title/owner/priority/status）；
- `settingsPage`：`defaultSprint`（text，默认 `sprint-42`）、`defaultOwner`（text，默认 `陈默`）、`weeklyTone`（select：简洁/正式）、`extraNote`（textarea）；
  action `GET stats`；
- `slashCommands`：`/weekly`（template `用 weekly_draft 生成本周周报初稿，再按风险清单补充“风险与求助”`，mode send）、
  `/risk`（template `用 risk_list 汇总 {arg} 的延期风险并给出处理建议`，mode insert）；
- 可选 `mentionProviders`：`endpoint: requirements`（候选需求），`readEndpoint: requirement-brief`。

## 4. 演示脚本

1. 「这个迭代进展如何？」→ `sprint_overview` → 目标、进度 62%、8 条需求（完成 2 / 进行 3 / 阻塞 1 / 待评审 2）；
2. 「谁手上活最多？」→ `requirement_query` → 陈默 3 条（含阻塞 1 条）→ 提示负载；
3. 「有哪些延期风险？」→ `risk_list` → 4 条，按高/中/低排序，逐条给建议；
4. 「帮我写本周周报」→ `weekly_draft` + `risk_list` → 输出 Markdown 周报，进展只写工具里的已完成/进行中；
5. 「新建一个需求：对账报表导出，陈默，P2」→ 复述后确认 → `requirement_create` → 返回 req-109「待评审」。

## 5. 验收红线

- [ ] 周报里的每条进展/风险都能在工具返回中找到依据，**不得编造“已完成”条目**；
- [ ] 风险等级与建议以 `risks` 预置数据为准，不自行发明新风险（可补充“建议关注”，但须标注为建议）；
- [ ] 创建需求确认前不落数据；成功后回显 id 与初始状态；
- [ ] 代码块能渲染 Markdown，长文本不截断到破坏结构。
