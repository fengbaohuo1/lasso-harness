# 应用 REST ↔ 插件工具桥（bridge 规范）

插件不是业务实现，只做一件事：**把应用 REST 翻译成 Agent 能调用的工具**，再把结果翻译回来。
本篇定四件事：应用 REST 怎么设计、工具怎么映射、插件怎么调应用、系统提示词怎么写边界。

## 1. 应用 REST 设计规范

### 1.1 路径与动作

| 动作 | 方法/路径 | 说明 |
| --- | --- | --- |
| 列表 | `GET /api/<domain>/<entity>?page=1&pageSize=20&<filter>` | 返回 `{total, page, pageSize, items:[...]}`；AI 工具默认 `pageSize=20` 足够 |
| 详情 | `GET /api/<domain>/<entity>/{id}` | 单条完整字段（含子集合，如明细/阶段/时间线） |
| 汇总 | `GET /api/<domain>/summary?<范围参数>` | **前端与大屏共用**；必须由明细实时计算，保证算术一致 |
| 创建/变更 | `POST /api/<domain>/<entity>`、`POST .../{id}/<action>` | 写操作；返回变更后的实体 + `before/after` |
| 插件专用入口 | `/api/bridge/**` | 与上面同 service，但要求 `X-Service-Token`（见 1.3） |

规则：
- `<domain>` 用业务词（`ledger`/`release`/`ticket`），不要用 `api1`；
- 过滤参数只允许**枚举与有限集合**（`status`、`category`、`month`、`page`），不要让 AI 传自由 SQL/正则；
- 空结果返回空数组 + `total:0`，不要返回 `null`；
- 字段用 camelCase、时间 `yyyy-MM-dd HH:mm`、金额用整数分或 `BigDecimal` 字符串（二选一并写进 README）。

### 1.2 统一错误码（应用侧，与底座区分开）

```json
{ "code": "00000", "info": "success", "data": { } }
{ "code": "40001", "info": "参数错误: pageSize 必须为 1..100", "data": null }
{ "code": "40004", "info": "记录不存在: T-9999", "data": null }
{ "code": "40009", "info": "非法状态流转: 已完成 -> 处理中", "data": null }
```

- `00000` 成功；`400xx` 参数/业务规则错误（可纠正）；`500xx` 内部错误（不可纠正）；
- 插件把 `400xx` 转成工具失败并保留原文，模型可据此换参数重试；`500xx` 不要暴露堆栈；
- HTTP 状态码统一 200（或按应用既有约定），**以 `code` 判断成败**——与底座一致，避免"200 但业务失败"两套判据。

### 1.3 serviceToken（插件 ↔ 应用）

推荐**路径分域**，不要让全局 Filter 误伤前端：

```text
/api/<domain>/**     ← 前端：无 token
/api/bridge/**       ← 插件：必须 X-Service-Token: <token>，内部委托同一 service
```

```java
// 只拦 /api/bridge/**，前端路径不经过（避免「AI 代理被 401」这类回归）
@Component
public class BridgeTokenFilter extends OncePerRequestFilter {
    @Value("${app.bridge-token:}") private String token;   // 空 = 未启用校验（仅本地演示）

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/bridge/");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (!token.isBlank() && !token.equals(req.getHeader("X-Service-Token"))) {
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":\"40100\",\"info\":\"service token 无效\",\"data\":null}");
            return;
        }
        chain.doFilter(req, resp);
    }
}
```

- token 用配置/环境变量注入（`APP_BRIDGE_TOKEN`），**不写进代码与 README 真实值**；
- 改 token 后要同步改插件配置（`PUT /api/harness/plugins/{id}/config`）并 `reload`；
- 若坚持"同一路径、插件也走 `/api/**`"，必须按**请求头 + 白名单路径**收窄，且回归测试覆盖前端页面流、AI 代理 SSE、Agent 端到端三条链路（有案例因为全局 token 校验误伤 AI 代理而整站不可用）。

## 2. 工具映射表（设计稿必产出）

每个工具一行，先定表再写码：

| 应用 API | 工具名 | 入参（schema） | 出参（JSON 结构） | description 要点 | 读/写 |
| --- | --- | --- | --- | --- | --- |
| `GET /api/bridge/monitor/services` | `service_list` | `env?`(string, 可选) | `{total,items:[{id,name,status,owner}]}` | 用户问「服务健康/有哪些服务」时先调；返回全量概览 | 读 |
| `GET .../metrics?service&from&to` | `metrics_query` | `service`(必填), `hours`(int, 默认24) | `{service,points:[{at,qps,p99}],summary:{...}}` | 问「指标/延迟/量」时调；无服务名先查 service_list | 读 |
| `GET .../alerts?status` | `alert_query` | `status?`(enum), `limit`(int) | `{total,items:[{id,level,at,state}]}` | 问「有没有告警/故障」时调 | 读 |
| `POST .../alerts/{id}/ack` | `alert_ack` | `alertId`(必填), `note?`(string) | `{ok:true,id,before,after}` | **写操作**：用户明确确认后才调用；先复述影响 | 写 |

约定：
- 工具名 = 「动词/名词组合 + 业务前缀」，蛇形（`release_gate_check`、`alert_ack`），≤32 字符；
- 默认暴露为 `plugin__<pluginId>__<tool>`；技能/命令里引用裸名时，在 `plugin.yaml` 设 `toolNamespace: global` 并保证全局唯一；
- 读工具返回**列表 + 总数 + 关键字段**，别返回整表；详情工具才返回子集合；
- 工具 `description` 三句话：**何时必须调用**（问真实数据必须走工具查证）、**何时不要调用**（闲聊/无数据需求）、**返回什么**（字段与单位）。写操作额外写「用户确认后才调用」与可回退性；
- 写工具的入参必须够用（id + 新状态 + 原因），缺参返回 `fail(...,"INVALID_ARGUMENT")`，不要"猜"。

## 3. 插件调应用（HTTP 客户端要点）

| 项 | 约定 |
| --- | --- |
| 超时 | 连接 1s / 读 3s（`timeoutMs` 可配，默认 3000）；超时即失败，不重试写操作 |
| 鉴权 | 每次请求带 `X-Service-Token`（`getConfig("serviceToken")`） |
| 地址 | `getConfig("baseUrl", "http://127.0.0.1:18081")`，即用即读 |
| 异常分类 | 连不上/超时 → `APP_UNAVAILABLE`；`400xx` → `APP_BAD_REQUEST`；`500xx` → `APP_INTERNAL_ERROR`；非信封 → `APP_BAD_RESPONSE` |
| 脱敏 | 日志只打 `method path status code`，不打 token、不打完整响应体（可能含用户数据） |
| 结果 | 返回**精简后的业务 JSON 字符串**给模型，不要原样塞整页响应 |

工具骨架（插件侧，`AbstractTool` 子类）：

```java
private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1)).build();

private String callApp(String method, String path, String bodyJson) {
    String baseUrl = getConfig("baseUrl", "http://127.0.0.1:18081").replaceAll("/+$", "");
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofMillis(Long.parseLong(getConfig("timeoutMs", "3000"))))
            .header("Accept", "application/json");
    String token = getConfig("serviceToken", "");
    if (!token.isBlank()) b.header("X-Service-Token", token);
    if ("GET".equals(method)) b.GET();
    else b.header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
    try {
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new AppBridgeException("APP_HTTP_" + resp.statusCode(), "应用返回 " + resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        String code = root.path("code").asText("");
        if (!"00000".equals(code)) {
            throw new AppBridgeException("APP_" + code, root.path("info").asText("应用返回业务失败"));
        }
        return root.path("data").toString();
    } catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
        throw new AppBridgeException("APP_UNAVAILABLE", "应用未启动或地址不可达");
    } catch (java.net.http.HttpTimeoutException e) {
        throw new AppBridgeException("APP_TIMEOUT", "应用响应超时（3s）");
    } catch (java.io.IOException e) {
        throw new AppBridgeException("APP_UNAVAILABLE", "应用连接中断");
    }
}
```

`run(...)` 里统一收口：

```java
@Override
protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
    String service = str(args, "service");
    if (service.isBlank()) return fail("service 不能为空", "INVALID_ARGUMENT");
    try {
        return ok(callApp("GET", "/api/bridge/monitor/metrics?service=" + enc(service), null));
    } catch (AppBridgeException e) {
        return fail(e.getMessage(), e.code());     // 结构化失败，别抛异常给基类兜底
    }
}
```

## 4. 系统提示词边界模板

在插件 `configure(ctx)` 里注册（**先 `super.configure(ctx)`**），段落名会变成 `plugin__<pluginId>__<name>`：

```java
context.registerSystemPrompt("bridge", 100, """
## 你在 <应用名> 的业务环境中工作
- 这里展示的是 <业务领域> 的真实演示数据；任何涉及具体数字、状态、时间、负责人、单据的问答，
  必须先调用本插件的工具查证后再回答，禁止凭常识或历史对话推测。
- 推荐链路：先用 service_list / <列表工具> 确定范围与 id，再用 <详情工具> 看细节，
  跨对象对比时分别调用对应工具，不要从一个工具的结果推断另一个对象的数据。
- 工具返回 error=true 或 code 非 00000 时，如实告诉用户失败原因，不要自行重试写操作；
  参数错误可换参数重试一次，仍失败就停下来说明。
- 写操作（<工具名列表>）：必须先复述"对哪个对象做什么变更"，得到用户明确确认后才调用；
  调用后按返回的 before/after 复述结果。
- 回答用中文；引用数字时带上口径（时间范围/单位），列表超过 5 条时先给结论再列前 5 条。
""");
```

提示词检查点：
- 每个「AI 亮点」都能对应到具体工具（指不出来就补工具或改能力）；
- 明确「无数据时说什么」（如「当前预置数据里没有 X」），而不是编一个；
- 不把长篇业务文档塞进提示词，细节让工具返回。

## 5. 联调顺序（省时间）

1. 应用 REST 单测/curl：列表、详情、汇总、写操作各一条；
2. 插件 `POST /api/harness/plugins/{id}/api/selftest`（建议加）：一次跑完所有工具，**不依赖模型**；
3. `GET /api/harness/plugins/{id}/contributions`：确认工具清单与端点都在；
4. `agent_stream` 逐工具实测（见 `references/delivery-checklist.md`）；
5. 最后才在浏览器面板里验 UI 与流式。

## 6. 安全红线

- 插件**不直连数据库/不持有业务凭据**，只走应用 REST；
- 只读默认：`execute`/`delete` 类能力必须白名单 + 用户确认 + 应用侧再校验；
- 应用侧对工具入参做**二次校验**（不信任模型），特别是 id、状态、金额、时间范围；
- 日志与错误信息脱敏：不打 token、不打完整记录、不回显 SQL；
- 演示数据用中性值（`10.x`、`prod`、虚构人名），不放真实客户/密钥/内网地址。
