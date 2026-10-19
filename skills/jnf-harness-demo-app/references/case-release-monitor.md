# 案例 P30 · 发布监控与回滚台（`demo-release-monitor`）

> 需求原话（插件版）：
> 「做一个发布监控台：看各服务的发布单和进度、单个发布单的分阶段详情、灰度门禁检查；AI 助手能判断“这次能不能继续放量、
> 要不要回滚”，并根据我的确认执行回滚，回显从哪个版本回到哪个版本。以 jnf-harness 插件形式提供。」

演示价值：**列表 → 详情 → 门禁判断 → 写操作回滚**，一条链上多个工具，最能体现 AI 联动与“人在回路”的确认。

## 1. 实体与预置数据

发布单 `releases`（6 条）

| id | service | version | status | progress | owner | startedAt |
| --- | --- | --- | --- | --- | --- | --- |
| rel-2041 | order-service | 2.8.0 | rolling | 60% | 陈默 | 2026-10-19 10:20 |
| rel-2040 | order-service | 2.7.4 | success | 100% | 陈默 | 2026-10-18 16:02 |
| rel-2039 | pay-service | 3.2.1 | **failed** | 40% | 林悦 | 2026-10-19 09:05 |
| rel-2038 | user-service | 1.14.2 | success | 100% | 周航 | 2026-10-18 11:31 |
| rel-2037 | pay-service | 3.1.9 | rolled-back | 100% | 林悦 | 2026-10-17 20:14 |
| rel-2036 | gateway | 5.0.3 | pending | 0% | 陈默 | 2026-10-19 11:00 |

阶段详情 `stages`（rel-2039，失败单）

| stage | status | durationSec | note |
| --- | --- | --- | --- |
| 构建 | success | 182 | image sha 9f2c1a |
| 单测 | success | 240 | 128 cases |
| 灰度 5% | success | 300 | 错误率 0.12% |
| 灰度 25% | **failed** | 180 | 错误率 0.91% > 阈值 0.5% |
| 全量 | pending | 0 | 未执行 |

灰度指标 `canary`（rel-2039）

| metric | baseline | canary | verdict |
| --- | --- | --- | --- |
| errorRate | 0.11% | **0.91%** | fail |
| p99Ms | 182 | 214 | warn |
| qps | 1240 | 1180 | ok |

门禁检查 `gate`（rel-2039）

| check | threshold | actual | passed |
| --- | --- | --- | --- |
| 错误率 | < 0.5% | 0.91% | false |
| P99 延迟 | < 200ms | 214ms | false |
| 单测 | 全部通过 | 128/128 | true |
| 变更审批 | 已批准 | 已批准 | true |

事件时间线 `incidents`（rel-2039，5 条）：09:05 开始灰度 → 09:12 错误率爬到 0.6% → 09:15 达到 0.91% 触发门禁 →
09:16 自动暂停 → 09:20 值班确认回滚。

## 2. 工具契约（读 4 + 写 1）

| name | 入参 | 返回 | description 要点 |
| --- | --- | --- | --- |
| `release_list` | `service`(可选) | `{"total":6,"releases":[{id,service,version,status,progress,owner,startedAt}]}` | 问“最近发布了什么/哪些失败了”时先调 |
| `release_detail` | `releaseId`(必填) | `{"release":{...},"stages":[{stage,status,durationSec,note}],"canary":[{metric,baseline,canary,verdict}]}` | 问“这单到哪一步/灰度数据如何”时调 |
| `release_gate_check` | `releaseId`(必填) | `{"releaseId","passed":false,"checks":[{check,threshold,actual,passed}],"blockers":["错误率 0.91% 超过阈值"]}` | 问“能不能继续放量/能不能发”时调；必须给出 blockers |
| `release_incident_timeline` | `releaseId`(必填) | `{"releaseId","items":[{at,event,actor}]}` | 问“发生了什么/为什么停”时调 |
| `release_rollback` | `releaseId`(必填), `reason`(必填) | `{"ok":true,"releaseId","fromVersion","toVersion","reason","operator":"current-user"}` | **写操作**：description 写明“用户确认后调用”；先复述影响再执行，返回 from→to |

错误语义：未知发布单 → `fail("发布单不存在: <id>","RELEASE_NOT_FOUND")`；对 `success` 单回滚 → `fail("该发布已成功，无需回滚","INVALID_STATE")`。

## 3. UI 映射

- `toolRenderers.release_list`：table（id/service/version/status/progress/owner）；
- `toolRenderers.release_detail`：kv（`result.release`）+ table（`result.stages`）+ table（`result.canary`）；
- `toolRenderers.release_gate_check`：kv（passed/blockers 计数）+ table（check/threshold/actual/passed）+ text（`result.blockers`）；
- `toolRenderers.release_rollback`：kv（fromVersion/toVersion/reason）+ text（operator）；
- `settingsPage`：`defaultService`（text，默认 `order-service`）、`canaryErrorThreshold`（number，默认 0.5）、`approverNote`（textarea）；
  action `GET stats`；
- `slashCommands`：`/release`，template `用 release_gate_check 检查 {arg} 是否满足放量条件，给出结论与建议`，mode insert；
- 可选 `mentionProviders`：`endpoint: releases`（候选发布单），`readEndpoint: release-brief`（返回一行摘要）。

## 4. 演示脚本

1. 「最近发布怎么样？」→ `release_list` → 6 条，1 失败（rel-2039）、1 滚动中（rel-2041 60%）、1 待发布；
2. 「rel-2039 卡在哪？」→ `release_detail` → 灰度 25% 失败，错误率 0.91%，P99 214ms；
3. 「能继续放量吗？」→ `release_gate_check` → passed=false，blockers：错误率、P99；结论：不要放量，建议回滚；
4. 「看看发生了什么」→ `release_incident_timeline` → 时间线复述（09:15 触发门禁 → 09:20 值班确认）；
5. 「回滚到 3.1.9，原因是灰度错误率超标」→ 复述“从 3.2.1 回滚到 3.1.9，需确认” → `release_rollback` → 回显 from/to/reason。

## 5. 验收红线

- [ ] 门禁结论必须以 `checks`/`blockers` 为准，**不得自行推断“应该没问题”**；
- [ ] 回滚前必须复述影响并等待确认；成功后必须回显 fromVersion → toVersion；
- [ ] 失败单与成功单状态区分清晰，`rolled-back` 单不得被当成“进行中”；
- [ ] 表格/键值块在空字段时隐藏而不是显示 `undefined`。
