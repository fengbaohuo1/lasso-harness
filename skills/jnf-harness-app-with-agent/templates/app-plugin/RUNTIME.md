# RUNTIME：独立产品运行时（jar 从哪来、为什么不入库、怎么升级）

本目录是**开发骨架**（`sample-app` + `sample-plugin` 多模块 Maven 工程）。要交付成独立产品时，
把 `start.ps1` / `stop.ps1` / `start.sh` 复制到产品的 `scripts/`，把三个 jar 复制到产品的 `runtime/`，
产品布局与启动顺序见 `../../references/product-packaging.md`。

```text
<product>/                       # ← 交付给用户的目录
├─ scripts/start.ps1|stop.ps1|start.sh   # 从本模板复制
├─ runtime/
│  ├─ jnf-harness-app.jar        # harness 内核（约 30MB，外部构建后复制）
│  ├─ plugins/sample-plugin.jar  # sample-plugin/target/sample-plugin.jar
│  ├─ app/sample-app.jar         # sample-app/target/sample-app.jar
│  └─ VERSION.txt                # 版本 + sha256（见下）
├─ data/                         # 首次启动自动创建（H2/events/插件目录/runtime.json）
└─ logs/
```

## 1. 三个 jar 分别从哪来

| 产物 | 命令（各自模块目录） | 来源 |
| --- | --- | --- |
| `runtime/jnf-harness-app.jar` | 在 `<harness>` 仓库：`mvn -o -B -DskipTests package` | `jnf-harness-app/target/jnf-harness-app.jar` |
| | 或 `node <harness>/scripts/package-local.mjs` | `dist/jnf-harness-local/jnf-harness-app.jar`（连同 `plugins/`、`plugin-sdk/`） |
| `runtime/plugins/sample-plugin.jar` | 在 `app-plugin/`：`mvn -o -B -DskipTests package` | `sample-plugin/target/sample-plugin.jar` |
| `runtime/app/sample-app.jar` | 同上 | `sample-app/target/sample-app.jar` |

复制命令（PowerShell，路径按实际替换）：

```powershell
New-Item -ItemType Directory -Force -Path <product>\runtime\plugins, <product>\runtime\app | Out-Null
Copy-Item <harness>\jnf-harness-app\target\jnf-harness-app.jar <product>\runtime\
Copy-Item .\sample-plugin\target\sample-plugin.jar <product>\runtime\plugins\
Copy-Item .\sample-app\target\sample-app.jar <product>\runtime\app\
```

## 2. 为什么不把 30MB jar 提交进仓库

- **体积**：单 jar 约 30MB，多次提交会把仓库撑大，clone/CI 拉取都变慢；
- **可复现**：jar 是构建产物，正确做法是记录「构建命令 + 版本 + sha256」，由构建流程产出，而不是提交二进制；
- **安全**：`data/runtime.json` 含 API Key、`data/harness/*.mv.db` 含会话与模型渠道，绝不能入库；
- **许可证/分发**：把运行时作为交付物单独分发（zip/内网制品库），与源码仓库分开管理更清晰。

产品仓库 `.gitignore` 至少包含：

```gitignore
runtime/*.jar
runtime/plugins/*.jar
runtime/app/*.jar
data/
logs/
```

`runtime/VERSION.txt` 必须入库（它是「这个产品用的是哪个 runtime」的唯一记录）：

```text
harness=0.1.0-SNAPSHOT sha256=<jar sha256 前 12 位> built=2026-10-24 java=17
plugin=demo-bridge version=1.0.0
app=sample-app version=1.0.0
```

生成/校验：`Get-FileHash runtime/jnf-harness-app.jar -Algorithm SHA256`（bash：`sha256sum`）。

## 3. 升级步骤

| 对象 | 步骤 | 说明 |
| --- | --- | --- |
| harness 内核 | `stop.ps1` → 备份 `data/` → 替换 `runtime/jnf-harness-app.jar` → 更新 `VERSION.txt` → `start.ps1` → 复验健康/插件/工具/应用 AI 流式 | 无 Flyway/Liquibase；跨版本可能 `Column ... not found`，必要时停进程删 `data/harness/*.mv.db` 重建（丢会话与渠道配置） |
| 插件 | 替换 `runtime/plugins/<plugin>.jar` → 重启；或 `POST /api/harness/plugins/<id>/reload` | 只换 jar 不 reload 仍是旧类加载器 |
| 应用 | `stop.ps1` → 替换 `runtime/app/<app>.jar` → `start.ps1` | `start.ps1` 会复用已健康进程，必须先 stop |
| 回滚 | 旧 jar + 旧 `data/` 成对回滚 | 只回滚 jar 不回 data，旧版本读到新 schema 仍会报错 |

升级后必须过的四条（任一条不过就不算升级成功）：

1. `http://127.0.0.1:18100/actuator/health` = UP；
2. `GET /api/harness/plugins` 中插件 `status=ACTIVE`（带 `X-API-Key`）；
3. `GET /api/harness/plugins/<id>/contributions` 工具数与设计稿一致；
4. 应用首页 200，且应用内 AI 助手能流式回答并调用到工具。

## 4. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `start.ps1` 报缺 `runtime/jnf-harness-app.jar` | 还没复制构建产物 | 按 §1 构建并复制；脚本不会自动构建（避免交付机没 Maven 时卡住） |
| 起不来但端口在听 | 上次进程没停，或开发共享底座占了应用端口 | `stop.ps1` 后重启；`Get-NetTCPConnection -LocalPort 18100,18081 -State Listen` 定位 |
| `Database may be already in use` | 同一份 H2 文件被两个进程打开 | 确认只有一个产品实例用这个 `data/`；不要与开发底座共用 `data/` |
| 重启后 API Key 变了 | 删掉了 `data/runtime.json` 或换了 `-ApiKey` | 正常现象；key 只影响 harness 鉴权，应用会自动跟随脚本注入 |
| 中文注释乱码 | Windows PowerShell 5.1 用 UTF-8 无 BOM 读脚本 | 改用 `pwsh`（PowerShell 7），或把脚本另存为 UTF-8 with BOM |
| `bash: $'\r': command not found` | 脚本在 Windows 上被保存成 CRLF，Linux 的 bash 不认 | `sed -i 's/\r$//' scripts/start.sh`，或 `.gitattributes` 里对 `*.sh` 设 `text eol=lf` |
| 用管道捕获 start 输出时「看起来卡住不返回」 | PowerShell 用 `&`/管道捕获时，会等脚本拉起的后台 java 释放继承的句柄才返回（服务不退出就一直等） | 终端里**直接运行**不受影响；自动化别 `\| Tee-Object` 捕获，改 `Start-Process pwsh -File start.ps1 -RedirectStandardOutput ...` 或直接读 `logs/`；本模板已在启动早期把地址打到屏幕 |
| Linux 怎么停 | 本模板只提供 Windows 的 `stop.ps1` | 读 `data/runtime.json` 的 pid：`kill -TERM <app pid>`、`kill -TERM <harness pid>`；确认端口释放 `ss -ltn \| grep -E '18100\|18081'` |
