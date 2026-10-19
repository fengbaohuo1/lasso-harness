# 案例 P28 · Redis 集群监控台（`demo-redis-monitor`）

> 需求原话（插件版）：
> 「做一个 Redis 集群监控台，能看到集群列表、节点指标、慢查询和 key 分布；AI 助手能回答“现在 Redis 怎么样、哪个节点拖后腿、
> 慢查询集中在哪些命令”，并支持把慢查询阈值调高调低（改了要告诉我旧值）。以 jnf-harness 插件形式提供，装好后可演示。」

演示价值：**指标表格 + 详情钻取 + 写操作**三类交互齐全，最接近“真实运维工具被 AI 接管”的场景。

## 1. 实体与预置数据

集群 `clusters`（2 条）

| id | name | env | version | nodes | status | qps | hitRate | memUsedMb / memMaxMb |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| redis-prod | 生产集群 | prod | 7.2.4 | 6 | healthy | 1832 | 0.982 | 12480 / 16384 |
| redis-canary | 灰度集群 | canary | 7.2.4 | 3 | warning | 214 | 0.964 | 2860 / 4096 |

节点 `nodes`（生产 6 条，节选；灰度 3 条同结构）

| node | role | qps | hitRate | memUsedMb | cpu | conn | status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 10.12.31.11:6379 | master | 612 | 0.981 | 4120 | 0.28 | 312 | ok |
| 10.12.31.12:6379 | replica | 588 | 0.980 | 4102 | 0.25 | 96 | ok |
| 10.12.31.13:6379 | master | 431 | 0.978 | 2240 | 0.74 | 508 | **slow** |
| 10.12.31.14:6379 | replica | 198 | 0.977 | 1990 | 0.22 | 71 | ok |
| 10.12.31.15:6379 | master | 3 | 0.0 | 18 | 0.02 | 4 | **down** |
| 10.12.31.16:6379 | replica | 0 | 0.0 | 16 | 0.01 | 2 | down |

慢查询 `slowlog`（6 条，minMs 默认 5）

| at | node | cmd | costMs | keys |
| --- | --- | --- | --- | --- |
| 2026-10-19 09:12:03 | 10.12.31.13:6379 | ZRANGEBYSCORE | 38 | 12 |
| 2026-10-19 09:10:47 | 10.12.31.13:6379 | KEYS | 31 | 1 |
| 2026-10-19 09:03:12 | 10.12.31.11:6379 | HGETALL | 18 | 1 |
| 2026-10-19 08:58:36 | 10.12.31.13:6379 | SMEMBERS | 14 | 1 |
| 2026-10-19 08:51:09 | 10.12.31.14:6379 | MGET | 9 | 24 |
| 2026-10-19 08:44:55 | 10.12.31.11:6379 | EXPIRE | 6 | 1 |

Key 分布 `keyscan`（`order:*` 模式预置 3 组样本，总匹配 128）

| sample key | count | ttl | memMb |
| --- | --- | --- | --- |
| order:20261019:{id} | 91234 | -1 | 213 |
| order:20261018:{id} | 88310 | 84213 | 206 |
| order:lock:{id} | 4210 | 12 | 3 |

## 2. 工具契约（读 4 + 写 1）

| name | 入参 | 返回（JSON 字符串） | description 要点 |
| --- | --- | --- | --- |
| `redis_cluster_list` | 无 | `{"clusters":[{id,name,env,version,nodes,status,qps,hitRate,memUsedMb,memMaxMb}]}` | 用户问“有哪些集群/现在怎么样”时先调 |
| `redis_node_metrics` | `clusterId`(string,必填) | `{"clusterId","summary":"6 节点 / 2 异常 / 总 QPS 1832","nodes":[{node,role,qps,hitRate,memUsedMb,cpu,conn,status}]}` | 问“哪个节点/是否均衡/谁拖后腿”时调 |
| `redis_slowlog` | `clusterId`(必填), `minMs`(number,默认 5), `limit`(number,默认 10) | `{"clusterId","minMs","total":6,"items":[{at,node,cmd,costMs,keys}]}` | 问“慢查询/卡顿”时调；按 costMs 倒序 |
| `redis_key_scan` | `clusterId`(必填), `pattern`(string,如 `order:*`) | `{"clusterId","pattern","matched":128,"samples":[{key,count,ttl,memMb}]}` | 问“大 key/某类 key 占多少”时调 |
| `redis_alert_threshold_set` | `clusterId`(必填), `metric`(enum: slowMs\|cpu\|memPct), `threshold`(number) | `{"ok":true,"clusterId","metric","oldValue","newValue","effectiveAt"}` | **写操作**：description 必须写“用户确认后才调用”；返回旧值→新值 |

错误语义：缺 `clusterId` → `fail("clusterId 不能为空","INVALID_ARGUMENT")`；未知集群 → `fail("集群不存在: <id>","CLUSTER_NOT_FOUND")`。

## 3. UI 映射

- `toolRenderers.redis_cluster_list`：table（columns：id/name/status/qps/hitRate/memUsePct），summary 用 `{result.summary}` 或节点数；
- `toolRenderers.redis_node_metrics`：kv（`result.summary`）+ table（node/role/qps/cpu/conn/status）；
- `toolRenderers.redis_slowlog`：table（at/node/cmd/costMs/keys）+ text（`result.minMs` 提示“阈值 N ms”）；
- `toolRenderers.redis_alert_threshold_set`：kv（`result.clusterId`/`result.metric`）+ text（`result.oldValue`→`result.newValue`）；
- `settingsPage`：`defaultCluster`（text，默认 `redis-prod`）、`slowMs`（number，默认 5）、`pageSize`（number，默认 10）、`notifyNote`（textarea）；
  action `GET stats`（插件端点返回工具调用次数）；
- `slashCommands`：`/redis`，argumentHint `集群 id`，template `用 redis_cluster_list 和 redis_node_metrics 检查 {arg} 的健康度，指出异常节点`，mode insert；
- 可选 `mentionProviders`：`endpoint: clusters` 返回集群候选，`readEndpoint: cluster-summary` 返回一段摘要文本。

## 4. 演示脚本（照读即可）

1. 「现在 Redis 怎么样？」→ `redis_cluster_list` → 回答：2 个集群，生产 healthy，灰度 warning（1 节点 down）；
2. 「生产哪个节点拖后腿？」→ `redis_node_metrics(redis-prod)` → 指出 `10.12.31.13` CPU 0.74、连接 508，`10.12.31.15/16` 已 down；
3. 「慢查询集中在什么命令？」→ `redis_slowlog(redis-prod, 5)` → `KEYS`/`ZRANGEBYSCORE` 为主，顺带提醒 `KEYS` 在线禁用；
4. 「把慢查询阈值调到 10ms」→ 先复述影响（旧值 5ms → 10ms，需确认）→ 用户确认后 `redis_alert_threshold_set` → 回显新旧值；
5. 「order 相关 key 占多少内存？」→ `redis_key_scan(redis-prod,"order:*")` → 128 个样本匹配，前三组样本内存合计约 422MB。

## 5. 验收红线

- [ ] 回答里的 QPS/命中率/内存必须与工具返回一致，**不得四舍五入到编造值**；
- [ ] 集群 down 的节点必须被点出，不能只报 healthy；
- [ ] 阈值写操作必须出现“确认”环节，且回显旧值；
- [ ] 表格块在 `items` 为空时隐藏，不抛异常。
