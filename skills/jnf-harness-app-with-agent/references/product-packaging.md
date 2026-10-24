# 独立产品打包（生产推荐）

把「应用 + 内嵌 AI 助手」交付成**自带运行时的独立产品**：一个目录、两条命令（start/stop），端口与数据与开发用的共享底座完全隔离。
开发期用共享底座（8090）迭代；用户说「交付 / 部署 / 独立运行 / 不要依赖你的仓库 / 生产」时，按本文打包。
脚本模板在 `templates/app-plugin/{start.ps1,stop.ps1,start.sh,RUNTIME.md}`，正文只讲规则与原因。

## 1. 两种模式怎么选

| 维度 | 开发共享底座（默认，省事） | 独立产品（交付/生产推荐） |
| --- | --- | --- |
| 底座 | 仓库构建的 8090 底座，多项目共用 | 产品自带 `runtime/jnf-harness-app.jar`，专用端口 **18100** |
| 数据 | 底座 `./data` 与所有项目混用 | 产品 `data/`，删目录即重置 |
| 插件 | 全体插件混在 8090 的 `plugins/` | 产品 `runtime/plugins/` 只放本产品插件 |
| 升级 | 改仓库源码即生效 | 替换 `runtime/*.jar` 后重启，可回滚 |
| 换机器 | 需要整个仓库 + Maven | 只要 JDK 17，解压即用 |
| 代价 | 0（本项目开发期） | 多一份约 30MB 二进制 + 启停脚本要维护 |

判定：**开发/联调/演示**用共享底座；**交付给用户、多产品并存、用户机器不装仓库**用独立产品。
不确定时默认共享底座，但在交付说明里写清「当前依赖开发底座 8090」，并给出切独立产品的条件。

## 2. 目录布局（复制即用）

```text
<product>/
├─ README.md                     # 四件套之一：使用/体验/生命周期/构建启动 + runtime 来源与数据目录
├─ scripts/
│  ├─ start.ps1                  # 一键启动（幂等）：harness → 插件 → 应用 → 探活 → 写 data/runtime.json
│  ├─ stop.ps1                   # 只停自己的进程（读 runtime.json + 校验命令行）
│  └─ start.sh                   # Linux/macOS 等价骨架（未在 Linux 实跑时须注明人工审查）
├─ runtime/                      # 二进制，不进版本库（.gitignore）
│  ├─ jnf-harness-app.jar        # 底座内核 fat jar（约 30MB）
│  ├─ plugins/<plugin>.jar       # 本产品插件（jar 同级 plugins/，启动自动装载）
│  ├─ app/<app>.jar              # 业务应用可执行 jar（spring-boot repackage 产物）
│  └─ VERSION.txt                # runtime 版本记录（见 §3）
├─ data/                         # 运行数据，不进版本库；删掉即重置
│  ├─ harness/                   # H2 文件库 jnf-harness.mv.db
│  ├─ events/                    # harness.events.dir 事件溯源
│  ├─ <plugin>/                  # 插件数据目录（插件自己读写的 dataDir）
│  └─ runtime.json               # start.ps1 写：端口/PID/地址/key（含密钥，勿外传）
└─ logs/
   ├─ harness.log                # harness stdout
   ├─ harness.err.log            # harness stderr（启动失败先看这个）
   ├─ app.log                    # 应用 stdout
   └─ app.err.log                # 应用 stderr
```

改名清单：`start.ps1` 顶部的 `$PluginId` / `$PluginEntrypoint` / 三个 jar 名；`README` 的服务地址表；
`VERSION.txt` 的版本号。目录名不必叫 `runtime`/`data`，但脚本与 README 必须一致。

## 3. 内置 runtime 从哪来

三种来源，**优先构建、其次分发包、最后才下载**：

| 来源 | 做法 | 适用 |
| --- | --- | --- |
| 源码构建 | 在 `<harness>` 执行 `mvn -o -B -DskipTests package`，拷 `jnf-harness-app/target/jnf-harness-app.jar` | 自己维护底座 |
| 分发目录（推荐） | `node <harness>/scripts/package-local.mjs` → `dist/jnf-harness-local/`（jar + `plugins/` + `plugin-sdk/` + start 脚本），整体复制到产品 `runtime/` | 交付机没 Maven |
| 官方发布包 | 取与源码同版本的发布 zip，解压后同上 | 外部用户 |

- **不提交 30MB jar**：`.gitignore` 忽略 `runtime/*.jar`、`data/`、`logs/`；交付物单独打包（zip / 内网制品库 / 网盘），README 写明获取方式。
- 版本记录 `runtime/VERSION.txt`（升级排障的唯一依据，必须写）：

```text
harness=0.1.0-SNAPSHOT sha256=<jar sha256 前 12 位> built=2026-10-24 java=17
plugin=<pluginId> version=1.0.0
app=<app> version=1.0.0
```

- 校验：`Get-FileHash runtime/jnf-harness-app.jar -Algorithm SHA256`（bash：`sha256sum`）。升级前后对不上，说明不是同一个 runtime。
- 同一台机器多个产品：各自 `runtime/`，互不共享插件目录；不要软链到开发仓库的 `target/`（仓库重建就断）。

## 4. 数据隔离清单（四项参数 + 两项位置）

全部靠**启动参数 + 工作目录**实现，不改 jar、不改源码：

| # | 项 | 做法 | 产品值 |
| --- | --- | --- | --- |
| 1 | H2 文件库 | `--spring.datasource.url` | `jdbc:h2:file:<product>/data/harness/jnf-harness;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE` |
| 2 | 会话事件 | `--harness.events.dir` | `<product>/data/events` |
| 3 | 插件数据目录 | `PUT /api/harness/plugins/{id}/config` 写入 `dataDir` | `<product>/data/<plugin>`；插件侧 `getConfig("dataDir")` 读写（宿主不提供默认目录） |
| 4 | 进程工作目录 | `Start-Process -WorkingDirectory` / `cd` | 产品根：`./data/skills.properties`、`./data/secrets.json`、`./data/attachments` 才会落在产品内 |
| 5 | 插件目录 | jar 同级 `plugins/` | `runtime/plugins/`（解析顺序 `harness.plugins.dir` → jar 同级 `plugins/` → `./plugins`） |
| 6 | 日志 | stdout/stderr 重定向 | `<product>/logs/*.log` |

要求与验证：

- 路径统一转**正斜杠**再拼 H2 URL（`$ProductRoot.Replace('\','/')`）；产品目录**避免空格与中文**（H2 连接串与命令行引用都更稳）；
- 启动后检查产品目录外没有新写入：`Get-ChildItem $env:USERPROFILE\.jnf` 不应出现本产品相关目录；
  若出现，说明插件没走 `dataDir` 而是用了相对路径（`ctx.cwd()` 之外的 `~/.<plugin>`）；
- `data/` 首次启动自动创建，**不要随交付物打包空 data**（否则换机器可能带着旧的 H2 锁文件）。

## 5. 端口规划

| 角色 | 开发共享底座 | 独立产品 A | 独立产品 B（多产品并存） |
| --- | --- | --- | --- |
| harness | 8090 | **18100** | **18110** |
| 应用 / BFF | 18081 起 | **18081** | **18082** |

- 产品 harness 从 18100 起，按 +10 递增；应用从 18081 起按 +1 递增，避免与开发期应用端口撞车；
- 一律显式 `--server.port=`（环境变量 `SERVER_PORT` 会劫持 Spring 端口，见 `runtime-pitfalls.md` §1）；
- 起前查占用：`Get-NetTCPConnection -LocalPort 18100 -State Listen`，
  起后核对实际监听；多产品并存时把端口写进各自 README 的地址表。

## 6. API Key（产品要开鉴权）

- **生成**：`start.ps1` 未传 `-ApiKey` 时先复用 `data/runtime.json` 里上次的 key，没有才 `(New-Guid).ToString('N')` 生成；也可 `-ApiKey <key>` 显式传入（CI/内网统一密钥）；
- **注入**：harness 启动参数 `--harness.auth.api-keys=<key>`；应用启动参数 `--harness.api-key=<key>`（模板 `harness.api-key: ${HARNESS_API_KEY:}`）；
- **边界**：浏览器只访问应用域，key 只存在应用服务端与 `data/runtime.json`；插件不需要 harness key（插件调应用 REST，用 `serviceToken`）；
- **验证**：无 key 调 `/api/harness/plugins` → HTTP 401；带 `X-API-Key` → 200；应用内 AI 面板仍能流式（服务端注入）；
- `data/runtime.json` 含明文 key：加入 `.gitignore`，交付说明里提醒「该文件含密钥，不要外传/截图」。

## 7. 一键启停：顺序与四条硬要求

启动顺序（每一步失败立即退出并打印日志路径与下一步命令）：

```text
校验 jar → 校验端口 → 起 harness(18100) → /actuator/health=UP
→ install + activate 插件 → PUT 插件配置(app.base-url / dataDir) → contributions 工具数 > 0
→ 起应用(18081) → 首页 200 → 写 data/runtime.json → 打印地址
```

四条硬要求：

1. **幂等**：harness/应用已健康则复用、不再 spawn；连续跑两次不新增进程、不改端口。判断依据是**健康接口**，不是端口在听（端口被别的程序占用要报错而不是复用）。
2. **只停自己**：`stop.ps1` 读 `data/runtime.json` 的 PID，用 `Get-CimInstance Win32_Process -Filter "ProcessId=<pid>"` 校验 `Name=java` 且 `CommandLine` 含产品根路径才 kill；PID 被系统复用时拒绝操作，宁可不杀。
3. **端口释放**：stop 后 `Get-NetTCPConnection -LocalPort 18100/18081 -State Listen` 应为空；仍在听说明有遗留进程或别的产品占用，打印提示而不是强杀。
4. **失败留痕**：任何一步失败输出 `[FAIL] 原因` + 日志路径 + 建议命令，退出码非 0；不要静默 continue 到「其实没起来」。

脚本骨架（PowerShell，直接抄模板；下面是每个参数/步骤的理由）：

```powershell
param(
    [string]$ProductRoot = (Split-Path -Parent $PSScriptRoot),  # 产品根：脚本在 <product>/scripts/ 下
    [int]$HarnessPort = 18100,                                 # 产品专用 harness 端口（共享底座仍是 8090）
    [int]$AppPort = 18081,                                     # 应用端口，多产品顺延
    [string]$ApiKey = ''                                       # 空 = 复用 runtime.json 或新生成
)
# 1) jar 检查：runtime/jnf-harness-app.jar、runtime/plugins/<plugin>.jar、runtime/app/<app>.jar
#    缺 harness jar → 提示 mvn 构建 / package-local.mjs；缺插件/应用 jar → 提示各自模块 package
# 2) 健康探活：Invoke-WebRequest <base>/actuator/health，body 含 "status":"UP" 才算活
# 3) 起 harness：Start-Process java -ArgumentList 显式 --server.port / datasource / events / api-keys
#    -WorkingDirectory 产品根；-RedirectStandardOutput/Error 到 logs/
# 4) 插件：POST /api/harness/plugins/install（sourcePath 用绝对路径）→ POST /activate → PUT /config
# 5) 起应用：--server.port / --harness.base-url=http://127.0.0.1:<harness 端口> / --harness.api-key
# 6) 写 data/runtime.json：status/apiKey/harness{port,pid,baseUrl}/app{...}/plugin{...}
```

> 为什么用 REST 再装一次插件，即使启动已自动装载？自动装载是兜底；显式 install+activate 让「插件 ACTIVE、工具数」成为启动脚本的验收点，失败能立刻报出来。

## 8. 升级与备份

| 操作 | 步骤 | 注意 |
| --- | --- | --- |
| 升级 harness | `stop.ps1` → 备份 `data/` → 替换 `runtime/jnf-harness-app.jar` → 更新 `VERSION.txt` → `start.ps1` → 复验 | 无 Flyway/Liquibase，跨版本可能缺列（`Column ... not found`）；必要时停进程删 `data/harness/*.mv.db` 重建（丢会话/渠道/插件安装记录） |
| 升级插件 | 替换 `runtime/plugins/<plugin>.jar` → 重启；或不重启 `POST /api/harness/plugins/{id}/reload` | 只换 jar 不 reload 还是旧类加载器；改配置后也要 reload |
| 升级应用 | `stop.ps1` → 替换 `runtime/app/<app>.jar` → `start.ps1` | start 会复用已健康进程，必须 stop 后再换 |
| 备份 | `stop.ps1` 后整体复制 `data/`（H2 + events + 插件数据 + runtime.json） | 运行中复制 H2 文件可能拿到半写状态 |
| 回滚 | 旧 jar + 旧 `data/` 成对回滚 | 只回滚 jar 不回 data，旧版本读到新 schema 仍会报错 |

## 9. 独立产品验收清单

- [ ] `runtime/` 三件 jar + `plugins/` 齐全，`VERSION.txt` 有版本与 sha256（对得上）
- [ ] 启动后 `data/` 自动生成；产品目录外无新写入（尤其 `~/.jnf`、`~/.<plugin>`）
- [ ] `http://127.0.0.1:18100/actuator/health` = UP；`http://127.0.0.1:18081/` = 200
- [ ] 插件 ACTIVE，`/contributions` 工具数 = 设计稿；每个工具至少一条实测（同 `delivery-checklist.md` 手工项）
- [ ] 鉴权：无 key 401 / 带 key 200；应用内 AI 面板流式正常（key 只在服务端）
- [ ] `start.ps1` 连续跑两次：第二次输出「复用」，`Get-Process java` 不多出新进程
- [ ] `stop.ps1` 后 18100/18081 均不在 Listen，`data/runtime.json` 标记 `STOPPED`
- [ ] `start.sh` 过了 `bash -n`；没实跑过的平台在 README 写明「未实跑，需人工验证」
- [ ] README 四件套 + runtime 来源 + 数据目录 + 备份说明齐全（见 `delivery-template.md` §六）
- [ ] 换一台只有 JDK 17 的机器：解压 → `start.ps1` → 打开地址可用（交付前真做一遍）

## 10. 相关文档

| 文件 | 关系 |
| --- | --- |
| `templates/app-plugin/start.ps1` / `stop.ps1` / `start.sh` | 可直接复制的启停骨架（含中文注释与失败提示） |
| `templates/app-plugin/RUNTIME.md` | runtime jar 来源、不入库原因、升级步骤 |
| `references/delivery-checklist.md` | 交付前逐条打勾（含「独立产品附加项」） |
| `references/runtime-pitfalls.md` §13 | 产品模式专用坑位（端口冲突、H2 锁、dataDir 漂移、jar 分发） |
| `references/delivery-template.md` §六 | 独立产品 README 必须补的字段 |
| `jnf-harness-ops` 技能 | 底座本身的构建/启动/数据目录/鉴权细节 |
