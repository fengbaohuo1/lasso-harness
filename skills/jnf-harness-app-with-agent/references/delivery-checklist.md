# 交付清单（应用 + 插件轨道）

用法：阶段 5 收尾先跑**自动项**，再逐条过**手工项**。全部打勾才允许交给用户。
清单来自真实踩坑：弹层关不掉（curl 测不出）、鉴权误伤 AI 代理（改完全局机制没回归）、Agent 卡 RUNNING（强杀流式请求）、地址是历史值（进程已被回收）。

## 一、自动项（脚本覆盖，跑完看输出）

```powershell
# 环境：Node / Java 17+ / Maven（--harness 指向 jnf-harness 仓库目录，用于找底座 jar）
node <skill-path>\scripts\check_env.mjs --harness <workspace>\jnf-harness

# 冒烟：底座探活 + 可选校验插件（--plugin 传 pluginId 时校验 ACTIVE 与 contributions）
node <skill-path>\scripts\smoke_test.mjs --base http://127.0.0.1:8090 --plugin <pluginId>

# 完整交付检查：底座探活 / 插件 ACTIVE / contributions / 应用探活（开了鉴权加 --api-key）
node <skill-path>\scripts\delivery_check.mjs --base http://127.0.0.1:8090 --plugin <pluginId> --app-url http://127.0.0.1:18081
```

| # | 检查项 | 通过标准 |
| --- | --- | --- |
| 1 | 底座探活 | `GET /actuator/health` → `{"status":"UP"}` |
| 2 | 应用探活 | 应用首页/健康接口 HTTP 200 |
| 3 | 插件状态 | `/api/harness/plugins` 中 `status=ACTIVE`（或 `runtimeState=ACTIVE`） |
| 4 | 注册面 | `/api/harness/plugins/{id}/contributions` 的 `tools` 数量 = 设计稿工具数 |
| 5 | 工具可发现 | Agent 侧工具名 = `plugin__<id>__<tool>`（`toolNamespace: global` 时是裸名） |
| 6 | 信封合规 | 管理端点返回 `{code,info,data}`，成功 `00000` |
| 7 | 鉴权回归（若开了 API Key） | 不带 key → 401；带 key → 200；**应用代理 SSE 仍通** |
| 8 | 端口正确 | 实际监听 = 约定端口（显式 `--server.port`，不吃环境变量） |
| 9 | 产品端口（仅独立产品模式） | 18100 `/actuator/health`=UP、18081 首页 200，且监听进程命令行含产品根路径（不是开发底座） |

**探活红线**：写进交付说明的地址必须是**刚刚**探活过的；进程被回收就重新拉起再交付，禁止照抄历史地址。

## 二、手工项：工具与数据

- [ ] **每个工具 ≥1 条自然语言触发实测**：用 `agent_stream.mjs` 逐个跑不同问法；核对 `step_break.toolName` 是预期工具、`tool_result` 非空、回答数字 = 工具返回
- [ ] 读工具覆盖列表/详情/汇总；**写工具单独验**：确认前不执行、确认后生效、返回 before/after
- [ ] **参数校验**：缺必填参数、非法 id、非法状态流转，全部返回结构化失败（不抛栈、不返回 500 页面）
- [ ] **算术一致**：汇总 = 明细逐条加总（自己算，不信页面），笔数/占比也对得上
- [ ] **写操作联动**：POST 真实数据 → 再查列表/汇总确认变化 → 重启应用还原种子数据
- [ ] **空数据分支**：查一个不存在的 id / 空月份，工具返回空而不是报错或编造
- [ ] 多工具联动至少 1 轮（列表 → 详情 → 结论/建议），体现 Agent 价值而不只是单查询

## 三、手工项：AI 面板（必须真浏览器）

- [ ] 浮钮能开、面板能关（关闭按钮 + 遮罩 + Esc），再开一次状态正常
- [ ] 发送后**逐字流式**出现，不是一次性蹦出；发送中「停止」可用，停止后输入恢复
- [ ] **markdown 渲染**：问一个会输出列表/加粗/标题的问题，确认渲染成排版而不是满屏 `**`
- [ ] **工具轨迹**：每次工具调用只出现一张卡（`step_break` 重复推送时靠 `callId` 去重），结果/失败标记正确
- [ ] `done` 后回答完整（以 `done.text` 为准），没有重复段落
- [ ] 错误路径：断开底座/停模型后提问，面板给出可读提示而不是空白或无限 loading
- [ ] 前端源码里搜不到 harness 地址与 API Key（`grep -i "8090\|api-key\|sk-"`）
- [ ] 刷新页面后新会话可正常提问（不依赖上一次内存状态）

## 四、手工项：应用 UI（curl 测不出「弹层关不掉」）

- [ ] 用真浏览器走核心路径：进入 → 筛选/搜索 → 打开详情 → 关闭 → 表单提交 → toast/联动
- [ ] 设计语言卡与实际页面一致；打开 3 秒能说出「这是哪类产品」；与邻近案例至少两项不同
- [ ] 空态/错误态/加载态都能看到；`[hidden]` 元素真的隐藏（无 CSS 写死 `display` 覆盖）
- [ ] 每个按钮/入口点一遍：无 JS 报错、无静默无效
- [ ] 375px 宽无横向滚动，导航与主按钮可用
- [ ] 键盘 Tab 一圈能完成主要操作；图标按钮有 `aria-label`

## 五、回归矩阵（改了全局机制必跑）

只要动了 Filter / 拦截器 / 统一异常处理 / CORS / 全局 token：

| 侧 | 用例 |
| --- | --- |
| 新规则生效 | 该拦的请求被拦（如 `/api/bridge/**` 无 token → 401） |
| 页面流完好 | 业务读写接口、静态资源正常 |
| AI 代理完好 | `POST /api/assistant/stream` 仍能流式输出 |
| Agent 端到端完好 | `agent_stream.mjs` 仍能调工具并返回 |

> 真实事故：给所有非 GET 加 token 校验，把浏览器直连的 AI 代理也拦了，用户看到「无法连接 AI 助手」。全局 Filter 必须按路径收窄。

## 六、交付物完整性（缺一不算交付）

- [ ] **探活过的地址**：应用 `http://<host>:18081` + 底座 `http://<host>:8090`，各配一句「能干什么」
- [ ] **工具清单**：`plugin__<id>__<tool>` 列表 + 读/写标注 + 一句话说明
- [ ] **README**（按 `references/delivery-template.md`）：使用说明、体验流程、工具清单、数据说明、构建启动、坑位
- [ ] **体验流程说明**：① 打开看到什么 ② AI 面板逐条示例问题（注明触发哪个工具）③ 底座控制台话术 ④ 写操作怎么体验
- [ ] **服务生命周期说明**：跑在哪个进程/机器、可能被回收、重启命令、内存数据重启还原
- [ ] **设计语言卡**：主色/材质/形状母题/标志性元素/气质词 + 与邻近案例差异
- [ ] **细腻度 7 项**（见 `references/case-library.md` 末节）：任一不过先修再交

## 七、平台已知问题（交付说明里提醒用户）

| 问题 | 表现 | 应对 |
| --- | --- | --- |
| 流式请求被强断后 Agent 卡 RUNNING | 后续请求报「正在执行上一条消息」 | 无 stop 端点，重启底座；自动化里别强杀流式请求进程 |
| `step_break` 重复推送 | 面板看到两次工具调用 | 单次执行只一条 `tool_result`，展示层按 `callId` 去重 |
| 未配置模型 | 对话直接报错 | 「设置 → 模型」添加渠道并测试 |
| 代理环境 curl 502 | 本机 curl 非 200 | 先确认端口在听，再用 `--noproxy`/`-NoProxy` |

## 八、验收闭环（交付后必做）

1. 交付时主动说：「打开两个地址过一遍体验流程，有要调的告诉我」；
2. 用户明确验收通过前任务不算完结；
3. 反馈迭代：小改重跑受影响工具 + 探活 + 触及的 UI 区块；大改（新增工具/改数据模型/改全局机制）重走设计确认 + 全链路验证 + 本清单；
4. 每次迭代同步更新 README 的工具清单与体验流程，禁止文档与实际能力脱节。

## 九、独立产品打包附加项（仅独立产品模式）

开发共享底座模式跳过本节。交付物是**独立产品**（自带 runtime、harness 18100、应用 18081、独立 `data/`）时逐条打勾；
规则、脚本与升级备份见 `references/product-packaging.md`，启停模板见 `templates/app-plugin/`。

```powershell
# 产品两个端口刚探活（写进 README 的地址必须是刚探过的）
Invoke-RestMethod http://127.0.0.1:18100/actuator/health                      # {"status":"UP"}
(Invoke-WebRequest http://127.0.0.1:18081/ -UseBasicParsing).StatusCode       # 200
# 插件与工具面（带产品 key；不带 key 应 401）
Invoke-RestMethod http://127.0.0.1:18100/api/harness/plugins -Headers @{ 'X-API-Key' = '<key>' }
node <skill-path>\scripts\delivery_check.mjs --base http://127.0.0.1:18100 --api-key <key> --plugin <pluginId> --app-url http://127.0.0.1:18081
```

- [ ] **runtime 齐全且可追溯**：`runtime/` 有 `jnf-harness-app.jar`、`plugins/<plugin>.jar`、`app/<app>.jar`；`runtime/VERSION.txt` 记了版本 + sha256，`Get-FileHash` 对得上
- [ ] **data 隔离**：`data/` 启动后自动生成；产品目录外无新写入（`Get-ChildItem $env:USERPROFILE\.jnf` 不应出现本产品目录，插件不得写 `~/.<plugin>`）
- [ ] **18100/18081 探活**：harness 健康 UP、应用首页 200；监听进程确属本产品（`Get-NetTCPConnection -LocalPort 18100,18081 -State Listen` 后核对命令行含产品根）
- [ ] **start 幂等**：连续跑两次 `scripts/start.ps1`，第二次输出「复用」，`Get-Process java` 不新增进程
- [ ] **stop 释放端口**：`scripts/stop.ps1` 后 18100/18081 都不在 Listen，`data/runtime.json` 标记 `STOPPED`
- [ ] **API Key 注入**：无 key 调管理端点 401；带 key 200；应用内 AI 面板仍能流式；前端源码搜不到 key
- [ ] **备份说明**：README 写了 `data/` 备份/恢复步骤（先停进程再复制）与升级/回滚方式（jar 与 data 成对）
- [ ] **README 四件套 + runtime 来源 + 数据目录**：使用说明、体验流程、工具清单、生命周期 + runtime 从哪来（构建/分发包 + 版本 + sha256）+ 数据目录说明 + 启停命令
- [ ] **换机器演练**：只装 JDK 17 的机器上解压 → `start.ps1` → 打开应用地址可用（交付前真做一遍，别假设）
