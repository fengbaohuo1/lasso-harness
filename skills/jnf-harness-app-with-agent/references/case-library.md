# 案例库与帮助菜单（应用 + 内嵌 AI 助手）

用途：用户说「help / 能做什么 / 没想好 / 有什么案例」时先给菜单；用户选定或粘贴一条 prompt 后，**原样作为需求输入**进入 SKILL.md 流程（阶段 0 只再确认「新项目还是已有应用」）。
每条 prompt 都按「业务实体 + 4~6 个工具 + 页面 + 预置数据 + AI 助手 + 以插件接入 jnf-harness」写全，无需用户补充端口/技术栈。

## 使用规则

1. **要全貌** → 回复「帮助菜单」三段式（能力介绍 + 分类菜单 + 引导）；
2. **要推荐** → 跨分类抽 3~5 个案例，原话术展示（不要只给标题），每个附一句「做出来是什么样」；末尾留「也可以直接描述任意应用」；
3. **用户选定** → prompt 即需求输入，进入设计稿阶段（实体/工具/页面/预置数据，确认后写码）；
4. **领域不在库里** → 按「Prompt 结构公式」现场生成同结构 prompt，案例库只负责灵感与质量标尺。

## 帮助菜单（help / 帮助 / 你能做什么 / 不知道做什么）

**第一段 · 一句话能力介绍**：
「我可以把一个业务需求做成完整可用的应用——**业务界面 + 内嵌 AI 助手 + 插件接入 jnf-harness**，交付可直接访问的地址；也可以给你已有的系统加上 AI 能力。下面这些案例，选一个就能开工。」

**第二段 · 分类菜单**：

```text
数据与运维   C01 数据库管理平台 · C02 发布监控台 · C10 值班巡检台
研发协作     C03 研发协作工作台 · C04 接口文档影响面
业务后台     C05 商城客服台 · C06 项目任务看板 · C07 仓储库存台
财务经营     C08 记账与预算 · C09 经营数据看板
行业应用     C11 诊所预约 · C12 在线课程 · C13 民宿预订 · C14 物流轨迹
内容与健康   C15 资讯社区 · C16 健身打卡
```

**第三段 · 引导**：
「回复编号或直接说业务领域都行（比如「做一个车队调度台」）。不确定的话告诉我三件事：谁用、管什么数据、想让 AI 帮他做什么，我来出设计稿。」

## 案例库（C01~C16）

### C01 数据库管理平台 ★深挖卡见下
> prompt：「做一个数据库管理平台：左侧连接列表（MySQL/PG，可测试连通性），中间库表树与字段详情，右侧分页数据网格与只读 SQL 查询框；AI 助手能根据自然语言生成只读 SQL 并解释结果、定位慢表和大字段、给出索引建议。预置 3 个连接、2 个库共 12 张表的样例数据。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「哪张表最大、为什么慢」→ 查表清单与字段 → 给索引/分页建议；「查一下最近 7 天创建但未激活的用户」→ 生成只读 SQL → 回显结果集。
- 工具：`db_connection_list`、`db_tree`、`db_table_columns`、`db_table_rows`、`db_query_readonly`（写操作 `db_execute` 需强确认，默认关闭）
- 数据/页面：连接侧栏（状态灯）+ 库表树 + 字段表 + 数据网格 + SQL 编辑器 + AI 侧栏；预置数据要有一条**故意慢的查询**与一张大表，AI 才有据可依。

### C02 发布监控与回滚台 ★深挖卡见下
> prompt：「做一个发布监控台：发布单列表（版本/环境/灰度比例/状态），单张发布单的阶段时间线与发布前后指标对比，灰度门禁检查清单，支持人工确认后回滚；AI 助手能判断能不能继续放量、解读错误率上涨、复述影响面后执行回滚。预置 8 张发布单覆盖全部状态。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「这次灰度能放量吗」→ 查门禁 → 引用未通过项给结论；「回滚到上一版」→ 复述影响 → 确认后执行并回显 from→to。
- 工具：`release_list`、`release_detail`、`release_gate_check`、`release_incident_timeline`、`release_rollback`（写）
- 数据/页面：发布单表 + 阶段时间线 + 指标对比表 + 门禁清单 + 回滚按钮（二次确认）；状态机 `待发布/灰度中/全量/已回滚` 各至少一条。

### C03 研发协作工作台 ★深挖卡见下
> prompt：「做一个研发协作工作台：MR 评审列表（评审人/意见/阻塞状态）、接口文档库（模块/入参出参/变更记录）、技术债清单（类型/风险/偿还建议）；AI 助手能总结评审的共识与分歧、检索接口变更的影响范围（关联调用方）、按风险排序技术债并给偿还计划。预置 12 个 MR、20 篇接口文档。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「改这个接口会影响谁」→ 查文档变更记录 → 反查调用方 → 输出影响面清单；「这次评审卡在哪」→ 聚合意见 → 分共识/分歧。
- 工具：`mr_list`、`mr_detail`、`api_doc_search`、`api_change_impact`、`tech_debt_rank`
- 数据/页面：看板三栏（MR/接口/技术债）+ 详情抽屉 + 影响面清单 + AI 侧栏；预置数据要有 1 个阻塞 MR、1 个 breaking change、3 条不同风险技术债。

### C04 商城智能客服台
> prompt：「做一个商城客服工作台：商品列表与详情、订单查询与物流轨迹、退换货工单；AI 客服能按订单号查进度、解释退换货政策、推荐替代商品，预置 12 个商品和 20 个订单。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「我的订单到哪了」→ 查订单 → 物流轨迹 → 给预计到达；「这个尺码不合适能换吗」→ 查政策 + 库存 → 给换货方案。
- 工具：`product_search`、`order_query`、`order_detail`、`logistics_track`、`ticket_create`（写）
- 数据/页面：商品网格 + 订单列表 + 物流时间线 + 工单表单 + AI 客服侧栏；订单状态 `待付款/已发货/已完成/退款中` 齐全。

### C05 项目任务看板
> prompt：「做一个团队任务看板：项目列表、任务卡片四列状态（待排期/进行中/待验收/已完成）、成员负载与燃尽图；AI 助手能总结本周进展、识别延期风险任务、按成员负载给排期建议。预置 4 个项目 30 个任务。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「这周能交付吗」→ 查燃尽与逾期任务 → 给风险清单；「谁最闲」→ 查成员负载 → 给分配建议。
- 工具：`project_list`、`task_query`、`task_detail`、`member_workload`、`task_update`（写）
- 数据/页面：看板四列 + 任务卡（负责人/截止/优先级）+ 燃尽图 + AI 侧栏；预置含 3 个逾期任务。

### C06 仓储库存台
> prompt：「做一个仓储库存管理台：SKU 列表（库位/库存/安全库存）、入库出库流水、缺货预警；AI 助手能判断哪些 SKU 需要补货、解释库存异常波动、按周转率给清库建议。预置 40 个 SKU 与 60 条流水。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「哪些货要补」→ 对比安全库存 → 按缺货紧迫度排序；「这个 SKU 怎么少了 200」→ 查流水 → 定位出库单。
- 工具：`sku_query`、`stock_alert`、`stock_flow`、`replenish_suggest`、`stock_adjust`（写）
- 数据/页面：库存表格（低于安全库存标红）+ 出入库流水 + 补货建议卡 + AI 侧栏。

### C07 记账与预算
> prompt：「做一个个人记账应用：收支流水、分类预算与超支提醒、月度趋势；AI 助手能解读本月超支原因、按消费习惯给省钱建议、模拟「少点外卖能省多少」。预置 3 个月 90 笔流水。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「这个月为什么超支」→ 分类汇总 → 对比预算 → 点出最大超支项；「少点外卖能省多少」→ 按流水统计外卖支出 → 给数字。
- 工具：`transaction_query`、`category_summary`、`budget_status`、`trend_query`、`transaction_create`（写）
- 数据/页面：流水表 + 分类环图 + 预算进度条 + 趋势线 + AI 侧栏；金额必须能逐条加总对上汇总。

### C08 经营数据看板
> prompt：「做一个门店经营看板：营业额/客单价/复购率趋势、分店对比、异常日期标注；AI 助手能解读某天营业额下跌原因、对比分店表现、给下周经营建议。预置 3 家店 90 天数据。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「上周三为什么掉这么多」→ 查当日明细 → 关联天气/活动备注 → 解释；「哪家店最值得扩」→ 对比增长与复购 → 给排序理由。
- 工具：`revenue_trend`、`store_compare`、`daily_detail`、`anomaly_query`、`report_generate`（写：生成周报）
- 数据/页面：KPI 卡行 + 趋势图 + 分店对比表 + 异常列表 + AI 侧栏；数字与明细一致。

### C09 诊所预约管理
> prompt：「做一个诊所预约管理台：医生排班、患者预约、叫号队列、病历摘要；AI 助手能按症状推荐科室、查医生余号、解释就诊流程（不输出诊断结论）。预置 8 位医生与 30 条预约。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「咳嗽一周挂哪个科」→ 按症状导诊（**只导诊不诊断**）→ 查余号；「周三上午还有号吗」→ 查排班 → 给时段。
- 工具：`doctor_schedule`、`booking_query`、`booking_create`（写）、`department_guide`、`queue_status`
- 数据/页面：医生卡 + 排班日历 + 叫号大屏 + 预约表单 + AI 侧栏；页面固定位置放免责声明。

### C10 值班巡检台
> prompt：「做一个生产值班巡检台：值班日历与交接事项、巡检清单（检查项/结果/异常）、告警事件流、SOP 知识库；AI 助手能汇总当日异常并关联 SOP 给处置步骤、自动生成交接摘要。预置两周值班记录与 15 条巡检。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「今天重点看什么」→ 查巡检异常 + 告警 → 关联 SOP → 输出清单；「帮我写交接」→ 汇总当班事件 → 生成交接摘要。
- 工具：`shift_calendar`、`inspection_query`、`alert_stream`、`sop_search`、`handover_generate`
- 数据/页面：值班日历 + 巡检表 + 告警流 + SOP 搜索 + AI 侧栏；巡检状态 `正常/异常/已处理` 齐全。

### C11 在线课程平台
> prompt：「做一个在线课程平台：课程列表（分类/难度/讲师）、课时目录与学习进度、笔记与提问；AI 学习助手能按基础推荐学习路径、解释课程知识点、总结我的薄弱环节。预置 8 门课程 60 个课时。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「我是零基础学哪个」→ 查课程难度与前置 → 给路径；「我哪块最弱」→ 查进度与错题/笔记 → 总结。
- 工具：`course_search`、`course_detail`、`learning_progress`、`note_query`、`path_recommend`
- 数据/页面：课程卡 + 课时列表 + 进度条 + 笔记面板 + AI 侧栏。

### C12 民宿预订
> prompt：「做一个民宿预订平台：房源列表（位置/价格/评分/标签）、房态日历、订单与评价；AI 助手能按预算和人数推荐房源、解释退订规则、对比相似房源。预置 10 套房源与 25 条订单。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「两大一小 800 以内住哪」→ 按条件筛房源 → 对比评分与位置；「提前三天退能退多少」→ 查规则 → 给金额。
- 工具：`stay_search`、`stay_detail`、`calendar_query`、`booking_create`（写）、`policy_explain`
- 数据/页面：房源卡（大图/价格/评分）+ 房态日历 + 订单列表 + AI 侧栏。

### C13 物流轨迹
> prompt：「做一个物流轨迹查询台：运单列表、轨迹时间线、异常件标注与处理；AI 助手能查运单进度、解读卡点原因、给延误安抚话术。预置 25 条运单覆盖各种状态。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「这个件为什么卡住」→ 轨迹 + 异常标注 → 解释卡点；「客户催件怎么回」→ 生成话术。
- 工具：`waybill_query`、`track_timeline`、`abnormal_query`、`waybill_note`（写）
- 数据/页面：运单表 + 轨迹时间线（节点圆点）+ 异常标签 + AI 侧栏。

### C14 资讯社区
> prompt：「做一个行业资讯社区：资讯流（分类/来源/时间）、详情与关联话题、收藏与评论；AI 助手能按兴趣筛选资讯、生成早报摘要、解释名词。预置 30 条资讯。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「今天有什么值得看」→ 按分类与热度筛 → 摘要；「什么是 XX」→ 查相关资讯 → 用已有内容解释（不编）。
- 工具：`news_query`、`news_detail`、`digest_generate`、`topic_related`、`favorite_toggle`（写）
- 数据/页面：资讯流（时间轴）+ 详情 + 话题标签 + AI 侧栏；时间与来源字段完整。

### C15 健身打卡
> prompt：「做一个健身打卡应用：训练计划、每日打卡记录、体重与围度趋势、连续打卡成就；AI 助手能根据目标给训练建议、解读进度停滞、调整计划。预置 8 周记录。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「我为什么卡在 70kg」→ 查记录与训练量 → 给调整建议；「下周练什么」→ 按计划与完成度 → 排课。
- 工具：`plan_query`、`checkin_query`、`body_trend`、`plan_adjust`（写）
- 数据/页面：打卡日历热力图 + 趋势线 + 计划卡 + AI 侧栏；数字来自预置记录。

### C16 车队调度台
> prompt：「做一个车队调度台：车辆列表（状态/位置/司机）、运单派发、里程与油耗统计；AI 助手能按运力与距离推荐派车、解释空驶率偏高的原因、生成调度日报。预置 12 台车与 40 条运单。以插件接入 jnf-harness，应用自带 AI 助手。」

- AI 亮点：「这单派哪台车」→ 查车辆状态与位置 → 给 2 个方案与理由；「空驶率为什么高」→ 查运单与轨迹 → 定位空返路线。
- 工具：`vehicle_list`、`dispatch_query`、`dispatch_create`（写）、`mileage_stats`、`route_suggest`
- 数据/页面：车辆地图/列表 + 派发单 + 统计卡 + AI 侧栏；状态 `空闲/在途/维修` 齐全。

## 深挖卡（写码前必做，案例 C01/C02/C03）

### C01 数据库管理平台深挖卡

- **实体**
  - 连接：`id/name/type[MySQL/PostgreSQL]/host/port/database/user/status[connected/failed]/lastTestAt`
  - 库表：`db/table/rows/sizeMb/engine/charset/comment`
  - 字段：`table/column/type/nullable/default/comment/isIndexed`
  - 查询记录：`id/sql/elapsedMs/rows/at/status`
- **工具表**：

| 工具 | 入参 | 返回 | description 要点 |
| --- | --- | --- | --- |
| `db_connection_list` | 无 | `{total,items:[{id,name,type,status,lastTestAt}]}` | 问「有哪些连接/哪个连不上」时先调 |
| `db_tree` | `connectionId`(必填) | `{connectionId,dbs:[{name,tables:[{name,rows,sizeMb}]}]}` | 需要表清单/找表时调；后续工具都要 connectionId |
| `db_table_columns` | `connectionId`,`table`(必填) | `{table,columns:[{column,type,nullable,default,isIndexed}]}` | 讨论字段/索引/类型时调 |
| `db_table_rows` | `connectionId`,`table`,`page`,`pageSize` | `{total,page,pageSize,rows:[...]}` | 看样例数据时调；单页 ≤50 行 |
| `db_query_readonly` | `connectionId`,`sql`(必填) | `{columns,rows,rowCount,elapsedMs}` | **只读**：只允许 SELECT；非 SELECT 一律 `fail("只允许只读查询","READONLY_ONLY")` |
| `db_execute` | `connectionId`,`sql` | `{affectedRows,sql}` | **写操作**：用户二次确认后才调用，默认在配置里关闭 |

- **页面**：三栏——左连接列表（状态灯 + 测试连通按钮）｜中库表树 + 字段表｜右数据网格 + SQL 编辑器 + 结果区；底部/右侧 AI 助手。
- **预置数据**：3 个连接（1 个 `failed`）、2 个库、12 张表（订单表 12000 行、用户表 8000 行、一张只有 3 行的小表）；1 条历史慢查询 1800ms。
- **演示脚本**：①「哪些表最大」→ `db_tree`；②「orders 表有哪些字段、索引够不够」→ `db_table_columns` → 给建议；③「查最近 7 天未激活用户」→ `db_query_readonly` → 回显行数与样例；④「删掉这条测试数据」→ 明确拒绝或要求确认后走 `db_execute`。
- **验收红线**：只读工具绝不能执行 DDL/DML；所有数值（行数/耗时）必须来自工具；慢查询结论必须引用实际耗时。

### C02 发布监控深挖卡

- **实体**：发布单 `id/service/version/env/canaryPercent/status[待发布/灰度中/全量/已回滚]/owner/startedAt`；阶段 `stage/status/durationSec/note`；指标 `metric[errorRate/p99Ms/qps]/baseline/canary/verdict`；门禁 `check/threshold/actual/passed`；事件 `at/event/actor`
- **工具表**：

| 工具 | 入参 | 返回 | description 要点 |
| --- | --- | --- | --- |
| `release_list` | `service?` | `{total,items:[...]}` | 问「最近发布了什么/谁失败了」时先调 |
| `release_detail` | `releaseId` | `{release,stages,canary}` | 问「到哪一步/指标如何」时调 |
| `release_gate_check` | `releaseId` | `{passed,checks,blockers}` | 问「能不能放量」时必调，**结论以 blockers 为准** |
| `release_incident_timeline` | `releaseId` | `{items:[{at,event,actor}]}` | 问「发生了什么/为什么停」时调 |
| `release_rollback` | `releaseId`,`reason` | `{ok,fromVersion,toVersion,reason}` | **写操作**：确认后执行，返回 from→to |

- **页面**：发布单表格（状态标签）→ 详情抽屉（阶段时间线 + 指标对比 + 门禁清单 + 回滚按钮）。
- **预置数据**：8 张单覆盖四状态；1 张失败单（错误率 0.91% > 阈值 0.5%，p99 214ms > 200ms）；1 张回滚单；事件时间线 5 条。
- **演示脚本**：①「最近发布怎么样」→ list；②「rel-2039 卡在哪」→ detail；③「能继续放量吗」→ gate_check → 不要放量；④「回滚到上一版，原因错误率超标」→ 复述影响 → rollback → 回显 3.2.1→3.1.9。
- **验收红线**：门禁结论不得自行推断；回滚必须二次确认且回显版本；已回滚单不得显示为「进行中」。

### C03 研发协作深挖卡

- **实体**：MR `id/title/author/reviewers/status[待评审/评审中/已合入/已关闭]/blocking/at`；评审意见 `mrId/reviewer/level[建议/阻塞]/content/resolved`；接口文档 `id/module/path/method/params/response/changelog`；技术债 `id/type[架构/测试/依赖/文档]/risk[高/中/低]/estimateDays/suggestion`
- **工具表**：

| 工具 | 入参 | 返回 | description 要点 |
| --- | --- | --- | --- |
| `mr_list` | `status?` | `{total,items:[...]}` | 问「有哪些评审/谁卡着」时先调 |
| `mr_detail` | `mrId` | `{mr,comments:[...]}` | 问「评审意见/分歧」时调 |
| `api_doc_search` | `keyword` | `{total,items:[{id,module,path,method}]}` | 找接口/看入参出参时调 |
| `api_change_impact` | `docId` | `{doc,changes,consumers:[{service,owner,contact}]}` | 问「改这个接口影响谁」时必调 |
| `tech_debt_rank` | `risk?` | `{items:[{id,type,risk,estimateDays}]}` | 问「先还哪笔债」时调，按风险排序 |

- **页面**：三栏看板（MR 评审 / 接口文档 / 技术债）+ 详情抽屉（意见列表 / 变更记录 / 影响面）+ AI 侧栏。
- **预置数据**：12 个 MR（1 个阻塞）、20 篇接口文档（1 处 breaking change 关联 3 个调用方）、8 条技术债（高/中/低分布）。
- **演示脚本**：①「哪些 MR 卡住了」→ mr_list → 1 个阻塞；②「这个 MR 的评审共识和分歧」→ mr_detail；③「改 /order/create 会影响谁」→ api_doc_search → api_change_impact → 3 个调用方；④「技术债按风险排一下」→ tech_debt_rank。
- **验收红线**：影响面必须来自 `consumers` 字段，不得凭接口名猜；评审总结要区分「共识/分歧」，不要一句话带过。

## Prompt 结构公式（库外案例）

```text
做一个 <领域> <形态：平台/工作台/看板/管理台>：
<核心区块 3~4 项，含列表/详情/写操作>，预置 <数据规模：几个实体各多少条、覆盖哪些状态>；
AI <角色>助手能 <3 个跨工具的智能能力，每个能指出依赖哪个工具>。
以插件接入 jnf-harness，应用自带 AI 助手（走 /api/assistant/stream 代理），交付可访问地址。
```

硬约束（设计稿阶段逐条核对）：
1. **每个 AI 能力必须能落到具体工具**，落不到就补工具或改能力描述，禁止让模型脱离数据推理；
2. 有「执行/变更/下单」的业务必须有**写工具 + ≥3 状态的状态机**，预置数据覆盖每个状态；
3. 涉及换算（金额/占比/等效值）的系数放在应用里走工具，禁止模型现算；
4. 工具 4~6 个为宜：少于 3 个体现不出联动，多于 6 个描述与调用都容易乱。

## 细腻度规范（简版）

**写预置数据前看这节，交付前逐条过。**

| 项 | 不合格 | 合格 |
| --- | --- | --- |
| 数据密度 | 每条 2~3 个字段 | 每条 ≥6 个有意义字段，首屏能看出是真实产品 |
| 名称 | 「路线A」「商品A」「测试数据」 | 真实业务文案：品牌/地名/编号/人名/具体日期 |
| 价格与数量 | 全整数、全相同 | 真实区间、有余有缺（37 件、剩 2 位） |
| 数据故事 | 所有记录同质 | 互相咬合：有满员也有剩位、有失败也有回滚、A 的评价引用 B 的商品 |
| 状态机 | 只有一种状态 | 核心实体 ≥3 状态，预置数据每个状态至少 1 条 |
| 算术 | 汇总与明细对不上 | 汇总=明细加总、进度=已完成/总量、金额=单价×数量 |
| 占位文案 | 全站出现「示例/测试/XXX」 | 全站搜不到，空态写人话 + 行动按钮 |
| AI 有据 | 回答凭空生成 | 每个数字都能在工具返回与页面上找到对应 |

验收动作：全站搜 `示例|测试|XXX|lorem`；手动加总一次汇总；让 AI 回答一个需要查数据的问题，去掉工具再问一次，确认它不会瞎编。
