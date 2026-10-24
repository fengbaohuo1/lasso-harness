---
name: jnf-harness-demo-app
description: 把一句话业务需求做成 jnf-harness 可点击的插件应用：需求深挖、工具契约、plugin-ui 前端贡献、打包安装激活与验收。含 Redis 监控、发布监控、研发协作 3 个即用案例。
when_to_use: 当用户要演示或交付一个业务 AI 应用、说“做一个 XX 管理台/监控台/工作台/看板”、或用案例编号（P28/P30/P31）点单时。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: demo
---

# 一句话 → 可点击的插件应用

本技能把“业务系统 + 内嵌 AI 助手”做成 **jnf-harness 插件**：业务数据与工具在插件里，界面由宿主的 `plugin-ui.json`
渲染（桌面端免重编壳），模型通过工具调用读数据、做联动分析。**不生成独立 Web 应用，不接真实生产系统**——案例均为
预置演示数据，用于评审、演示与验收，真接系统另行评估。

## 0. 先确认三件事（只问这三件，别多问）

1. **案例**：用户给了编号/名称 → 用对应 references；没给 → 从文末“案例速览”抽 2~3 个让用户点；
2. **插件形态**：默认独立插件（id 形如 `demo-redis-monitor`）；用户要求并入已有插件时，沿用其 pluginId 与包名；
3. **名字**：中文界面名可自定义，**pluginId / 工具名 / 目录名保持 kebab/snake 且不重复**。

其余信息（实体、工具、页面、预置数据）全部由本技能按案例卡推导，**不要再向用户索取**。

## 1. 硬规则（违反即重做）

- **先设计稿，后写码**：拿到需求先按第 2 步出设计卡（实体/工具/区块/数据/演示脚本），禁止直接写 Java；
- **数据必须来自工具**：界面与回答里的数字只能取自工具返回；无数据时显示空态，**禁止编造**；UI 里不写死数字模板；
- **只读默认，写操作二次确认**：改阈值/回滚/建需求类工具，description 必须写明“用户确认后才调用”，执行前再校验参数；
- **工具返回 JSON 字符串**（`ok("{\"a\":1}")`），字段与 `plugin-ui.json` 的 `result.*` 点路径一一对应；
- **工具名加业务前缀**（`redis_` / `release_` / `sprint_`），避免与官方工具冲突；不要使用宿主保留的 `plugin__` 前缀；
- **不写密钥、不写本机绝对路径**；演示数据用 `10.x`/`prod` 这类中性值。

## 2. 设计卡（写到仓库里的 `docs/<pluginId>-design.md` 或直接作为回复）

| 项 | 要求 |
| --- | --- |
| 实体表 | 3~5 个实体，字段够渲染真实页面（名称/状态/数值/时间/负责人等） |
| 工具表 | 每个工具的 name、入参 schema、返回 JSON 结构、description、读/写属性 |
| 页面区块 | 列表页/详情页/操作区，对应到 `toolRenderers.blocks`（kv/table/code/text/actions） |
| 预置数据 | 每个实体 5~8 条，状态分布合理，中文业务文案、价格/数值在合理区间 |
| 演示脚本 | 3~5 轮对话：每轮的问题 → 该调哪些工具 → 回答要点 |

页面设计只允许用宿主 blocks 能表达的东西（表格/键值/代码/文本/动作按钮/配置表单），**没有自由 HTML**。

## 3. 生成插件工程

以 `jnf-harness/plugins/sample/` 为模板复制改造（比从零写快）。演示插件按**第三方独立工程**处理，放在仓库外的独立目录
（默认 `<workspace>/plugins/<pluginId>/`，也可由用户指定任意路径；本技能不要求工程必须在工作区内）：

```
<workspace>/plugins/<pluginId>/
├─ pom.xml                                   # artifactId=<pluginId>；parent 用 relativePath ../../jnf-harness/pom.xml
└─ src/main/
   ├─ java/com/jnf/harness/demo/<pkg>/<PluginClass>.java
   └─ resources/META-INF/
      ├─ plugin.yaml                         # id/name/version/author/description/entrypoint/sdkVersion:1/ui:META-INF/plugin-ui.json
      ├─ plugin-ui.json                      # 前端贡献清单
      └─ services/com.jnf.harness.spi.JavaHarnessPlugin   # 内容=实现类全限定名
```

- **不加入** jnf-harness 的 Maven reactor（根 `pom.xml` 不登记）；只有确定要随宿主发布的官方插件，才移入
  `jnf-harness/plugins/<短名>/` 并加进根 `<modules>`；
- 依赖：`jnf-harness-types`（`scope=provided`）+ `slf4j-api`（provided）；SDK 版本 `SdkVersion.CURRENT=1`；
- **插件自有依赖必须打包**：宿主 ClassLoader 不共享第三方库；需要 JDBC 驱动/模板引擎等时，用 `maven-shade-plugin`
  打成胖 JAR（保留 `META-INF/services`），否则运行期 `NoClassDefFoundError`；
- **工具命名空间**：默认注册为 `plugin__<id>__<tool>`；若技能/命令模板里用裸工具名（如 `db_query`），在
  `plugin.yaml` 写 `toolNamespace: global`，并保证全局唯一；
- **插件数据目录**：宿主不提供；用配置项（如 `dataDir`，默认 `~/.<plugin>`）+ 绝对路径，避免依赖进程 cwd；
- 若生成器选择把工程放在别处（`jnf-harness` 不在同级），则**去掉 parent**，给 `jnf-harness-types` 显式写
  `<version>0.1.0-SNAPSHOT</version>`，slf4j-api 用 2.0.x，二者均 `provided`；
- 插件类继承 `AbstractHarnessPlugin`，构造传 pluginId；`tools()` 返回工具，`configure(ctx)` 里注册提示词/端点/Hook；
- 每次请求的工具数据：直接放插件内存 `Map`（进程内重启即还原），演示足够；不要读数据库。

最小工具骨架：

```java
public static class RedisClusterListTool extends AbstractTool {
    @Override public String name() { return "redis_cluster_list"; }
    @Override public String description() { return "列出 Redis 集群概况（演示数据）。用户问“Redis 怎么样/有几个集群”时调用。"; }
    @Override public Map<String, Object> parameters() { return objectSchema().build(); }
    @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        return ok("{\"clusters\":[{\"id\":\"redis-prod\",\"name\":\"生产集群\",\"status\":\"healthy\",\"qps\":1832}]}");
    }
}
```

写操作在 `run` 里二次校验（缺参/非法值返回 `fail("...", "INVALID_ARGUMENT")`），成功后回显“改了什么、旧值→新值”。

## 4. 前端贡献清单（`META-INF/plugin-ui.json`，schemaVersion=1）

四类贡献，按需选（没界面的插件可以不写）：

| 键 | 作用 | 关键约束 |
| --- | --- | --- |
| `toolRenderers` | 工具卡渲染 | 键 = **工具名**；`title`/`summary` 模板支持 `{args.x}`/`{result.x}`；`blocks` 的 `source` 是点路径，取不到则整块隐藏 |
| `settingsPage` | 独立配置页 | `sections[].fields` 类型：text/password/number/checkbox/select/textarea；值与插件配置 KV 互转；`actions` 的 `bodyFrom:"form"` 提交整个表单 |
| `slashCommands` | 命令面板快捷入口 | `template` 里 `{arg}` = 命令后随行参数；`mode` = insert（默认）或 send |
| `mentionProviders` | `@` 提及源 | `endpoint` 返回 `{"items":[{value,label,description}]}`；`readEndpoint` 接收 `?path=` 返回 `{"content":"..."}` |

blocks 类型：`kv` / `table`（需 `columns:[{key,label}]`）/ `code`（`language`）/ `text` / `json` / `actions`（`items:[{label,method,path,confirm?}]`）。

示例（节选，完整写法见 `jnf-harness-plugin-dev/references/plugin-ui-manifest.md`）：

```json
{
  "schemaVersion": 1,
  "toolRenderers": {
    "redis_node_metrics": {
      "title": "节点指标 · {args.clusterId}",
      "summary": "{result.summary}",
      "blocks": [
        { "type": "table", "label": "节点", "source": "result.nodes",
          "columns": [{ "key": "node", "label": "节点" }, { "key": "qps", "label": "QPS" }] }
      ]
    }
  },
  "settingsPage": {
    "title": "监控偏好", "sections": [
      { "title": "默认值", "fields": [{ "key": "defaultCluster", "label": "默认集群", "type": "text", "default": "redis-prod" }] }
    ],
    "actions": [{ "label": "查看调用统计", "method": "GET", "path": "stats" }]
  },
  "slashCommands": [
    { "name": "redis", "description": "Redis 集群体检", "argumentHint": "集群 id", "template": "用 redis_cluster_list 和 redis_node_metrics 检查 {arg} 的健康度", "mode": "insert" }
  ]
}
```

配置读取：`context.getConfig("defaultCluster", "redis-prod")`；配置页保存走宿主 KV，工具执行时按 key 读取。

## 5. 打包与上线（每步都要有可见结果）

```bash
# 首次：把 SDK 装进本地仓库（插件 provided 依赖解析用）
cd jnf-harness && mvn -o -B -pl jnf-harness-types -am install -DskipTests

# 构建插件（在 <workspace>/plugins/<pluginId>）
mvn -o -B -DskipTests package

# 放进宿主插件目录（宿主的“jar 同级 plugins/”；命令行启动即 jnf-harness-app/target/plugins）
cp target/<pluginId>-*.jar <workspace>/jnf-harness/jnf-harness-app/target/plugins/
```

桌面端随包：先执行上面的宿主构建（或 `npm run agent:build`），把 jar 放进 `jnf-harness-app/target/plugins/` 后，
在 `jnf-desktop` 执行 `npm run agent:copy`（会把该目录所有 jar 复制进 `resources/agent/plugins/`），再 `npm run build` 打包。

```bash
# 目录扫描 → 安装 → 激活（agent 用 curl/shell 工具执行）
curl -s localhost:8090/api/harness/plugins/catalog          # 目录里的插件默认已自动装载
curl -s -X POST localhost:8090/api/harness/plugins/install -H "Content-Type: application/json" \
  -d '{"pluginId":"demo-redis-monitor","displayName":"Redis 监控台","pluginVersion":"1.0.0","runtimeType":"JAVA_NATIVE","sourcePath":"demo-redis-monitor-1.0.0.jar","entrypoint":"com.jnf.harness.demo.redis.RedisMonitorPlugin"}'
curl -s -X POST localhost:8090/api/harness/plugins/activate -H "Content-Type: application/json" -d '{"pluginId":"demo-redis-monitor"}'
curl -s localhost:8090/api/harness/plugins/demo-redis-monitor/contributions   # 一屏核对注册了什么
```

- **自动装载**：插件放进目录后启动即 ACTIVE（catalog 里 `installed=true`）；`activate` 幂等，脚本可无脑重试；
- **免模型自检**：给插件加 `POST /api/harness/plugins/{id}/api/selftest` 端点，一次跑通所有工具并返回每步结果；
  排障顺序：`contributions` → `health` → `selftest` → 模型对话。

桌面端：重启后端进程后，在「设置 → 插件」点刷新/重载插件 UI 即可看到工具卡与配置页；改完 `plugin-ui.json` 用「重载插件 UI」免重启。
替换 jar 后调 `POST /api/harness/plugins/{id}/reload`。

## 6. 验收清单（逐条过，任一条不过不算交付）

- [ ] 工具全部可调用；参数缺失/非法时返回结构化错误，不抛栈；
- [ ] 每个工具卡按 `blocks` 渲染出表格/键值/文本，空数据时块隐藏而不报错；
- [ ] 3~5 轮演示对话能体现**多工具联动**（列表→详情→建议/对比），回答里的数字都能对上工具结果；
- [ ] 写操作被明确标注“需确认”，确认前不落数据；
- [ ] 配置页能保存并生效（改默认值后工具行为跟着变）；
- [ ] `/命令` 能插入模板并触发正确工具；
- [ ] 插件自带 `selftest` 端点在宿主里全部通过（免模型即可验收）；
- [ ] 无密钥、无本机路径、无真实生产数据。

## 7. 排障速查

| 现象 | 优先检查 |
| --- | --- |
| catalog 为空 | jar 是否在插件目录；插件目录是否宿主解析的那个（jar 同级 `plugins/` → `./plugins`） |
| install 报“制品不存在” | `sourcePath` 传**相对文件名**，且 jar 确实在该目录 |
| activate 失败 | `sdkVersion` > 1、`entrypoint` 类不存在、缺 `services/` 注册文件 |
| 工具没注册 | 工具类是否在 `tools()` 里返回；name 是否与官方工具撞名 |
| 工具卡没样式 | `toolRenderers` 键必须等于工具名；工具结果必须是可解析 JSON 字符串；点路径写错则块静默隐藏 |
| 配置页按钮 404 | 动作 `path` 必须与 `registerEndpoint(method,path,...)` 一致，且 method 匹配 |
| 配置改了不生效 | 插件侧是否 `getConfig("<同一 key>")`；保存后是否触发了新的一次工具调用 |
| 桌面看不到新 UI | 「设置 → 插件 → 重载插件 UI」；确认插件状态 ACTIVE |

## 8. 案例速览

| 编号 | 案例 | 演示价值 | 设计卡 |
| --- | --- | --- | --- |
| P28 | Redis 集群监控台 | 指标表格 + 慢查询 + 阈值变更（写操作），最像真实运维工具 | [references/case-redis-monitor.md](references/case-redis-monitor.md) |
| P30 | 发布监控与回滚台 | 版本列表 → 灰度指标 → 门禁检查 → 回滚（强确认），多工具链路最能体现联动 | [references/case-release-monitor.md](references/case-release-monitor.md) |
| P31 | 迭代协作工作台 | 需求看板 + 风险 + 周报生成，偏“AI 助手”日常用法 | [references/case-dev-collab.md](references/case-dev-collab.md) |

库外领域：按第 2 步同结构推导（业务实体 + 4~6 个工具 + 列表/详情/操作区 + 5~8 条预置数据 + 3 轮演示脚本），同样可执行。
