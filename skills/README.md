# jnf-harness 技能包目录

本目录是 jnf-harness 的**技能包（SKILL.md）**默认自定义根（`harness.skills.roots` 默认值 `./skills`，相对宿主进程工作目录）。
技能目录摘要会进入系统提示词，模型按需调用 `skill` 工具加载正文后执行。

## 目录布局

```text
skills/
├── README.md                                  # 本文件
├── _template/
│   └── SKILL.md.template                      # 新建技能时复制（不以 SKILL.md 命名，不会被扫描）
├── jnf-harness-plugin-dev/
│   ├── SKILL.md
│   └── references/
│       ├── plugin-ui-manifest.md
│       └── common-errors.md
├── jnf-harness-ops/
│   └── SKILL.md
├── jnf-harness-demo-app/
│   ├── SKILL.md
│   └── references/
│       ├── case-redis-monitor.md
│       ├── case-release-monitor.md
│       └── case-dev-collab.md
└── skill-authoring/
    └── SKILL.md
```

## 四个技能

| 技能 | 定位 | 典型触发 |
| --- | --- | --- |
| `jnf-harness-plugin-dev` | 给 jnf-harness **写 Java 插件**：SPI 选型、工具注册、`plugin.yaml`、Hook/`Endpoint`/配置、打包与安装 | “写一个插件”“注册新工具”“plugin.yaml 怎么写”“插件热重载” |
| `jnf-harness-ops` | 本地**构建、启动与运维**：端口/鉴权/模型渠道/数据目录/技能与插件目录/故障排查 | “启动失败”“端口占用”“401”“配置模型渠道”“data 在哪” |
| `jnf-harness-demo-app` | 把**一句话业务需求**做成可点击插件应用：需求深挖卡 → 工具契约 → `plugin-ui.json` → 打包安装激活 → 验收；含 Redis 监控 / 发布监控 / 迭代协作 3 个即用案例 | “做一个 XX 管理台/监控台/工作台”“按 P28 做一个” |
| `skill-authoring` | **写合规技能包**：frontmatter、命名、描述、渐进式披露、references/scripts、自检清单 | “新增一个技能”“技能没被发现”“技能怎么写” |

> `jnf-harness-demo-app` 的 3 个案例是**演示应用**（预置数据、不接真实系统），设计卡在 `references/case-*.md`；
> 生成物建议放仓库外的 `demo-plugins/<pluginId>/`，构建后把 jar 放进宿主插件目录。

## 发现与优先级

| 来源 | 路径 | 优先级 |
| --- | --- | --- |
| PROJECT | `<cwd>/.jnf/skills/` | 最高 |
| CUSTOM | `harness.skills.roots`（默认 `./skills`，逗号分隔多根） | 中 |
| USER | `harness.skills.user-dir`（默认 `~/.jnf/skills/`） | 低 |
| BUNDLED | 随包内置（枚举预留，文件系统发现未实现） | 最低（约定） |

- 同名技能，高优先级覆盖低优先级。
- 单技能可用 `SKILL.md`（目录包）或 `<name>.md`（平铺包）；根目录下裸 `SKILL.md` 会被忽略。
- 技能文件增删改**无需重启**宿主；改 `harness.skills.*` 配置需重启。
- 摘要目录上限：最多 50 条、合计 4000 字符（每条描述截断 200 字符）。

## 把本目录作为 custom root

默认已经是 `./skills`，从仓库根启动宿主即可，无需配置。需要显式指定时：

```powershell
# 命令行参数（相对宿主进程工作目录）
java -jar jnf-harness-app\target\jnf-harness-app.jar --harness.skills.roots=./skills

# 多根：逗号分隔
java -jar jnf-harness-app\target\jnf-harness-app.jar --harness.skills.roots=./skills,./team-skills
```

```bash
java -jar jnf-harness-app/target/jnf-harness-app.jar --harness.skills.roots=./skills
```

用环境变量兜底时，写成 Spring 占位符（注意用单引号避免 shell 展开）：

```powershell
java -jar jnf-harness-app\target\jnf-harness-app.jar '--harness.skills.roots=${JNF_SKILLS_ROOTS:./skills}'
```

- `${JNF_SKILLS_ROOTS:./skills}` 含义：优先环境变量 `JNF_SKILLS_ROOTS`，未设置则用 `./skills`。
- 也可用 `HARNESS_SKILLS_ROOTS`（Spring 环境变量源自动映射 `harness.skills.roots`）。
- 其他相关键：`harness.skills.user-dir`（用户技能目录）、`harness.skills.state-file`（禁用集合，默认 `./data/skills.properties`）。

## 安装用户技能（三种方式）

1. **桌面端**：设置 → 技能 → 安装，填 Git URL、可选 `subdir`（如 `skills/my-skill`）、可选 `name`（缺省取 frontmatter 的 name）。
2. **模型工具**：`extension_skills`，`action=install`，`source=<Git URL>`，可选 `name`。
3. **REST**：

```powershell
Invoke-RestMethod -Method Post "$base/api/harness/skills/install" -Headers $h -Body (@{
  gitUrl = "https://github.com/<org>/<repo>.git"
  subdir = "skills/my-skill"
  name   = "my-skill"
} | ConvertTo-Json)
```

安装规则与限额：

- 只支持 `https://`、`http://`、`git@`、`ssh://`；浅克隆、120s 超时。
- `subdir` 必须**直接**包含 `SKILL.md`；不给 `subdir` 时在仓库内按深度 4 搜索第一个含 `SKILL.md` 的目录。
- 安装到用户技能目录（默认 `~/.jnf/skills/<name>`）；同名已存在会拒绝。
- 拒绝符号链接；单文件 ≤8MB、总量 ≤32MB、文件数 ≤500；zip 另限 ≤1000 条目、解压总量 ≤64MB。
- 桌面端“技能”面板按来源分组：PROJECT / CUSTOM / USER / BUNDLED；启用/禁用/删除只对 USER 生效（项目级与内置不可删）。
- 本地 zip 安装接口也支持（`zipPath` 字段），但桌面 UI 未暴露。

## 新增一个技能

1. 复制模板：

```powershell
Copy-Item -Recurse skills\_template skills\my-skill
Rename-Item skills\my-skill\SKILL.md.template SKILL.md
```

```bash
cp -r skills/_template skills/my-skill && mv skills/my-skill/SKILL.md.template skills/my-skill/SKILL.md
```

2. 改 frontmatter：`name` 与目录名一致（kebab-case）；写清 `description` / `when_to_use`。
3. 写正文：自包含、可执行、含验证与故障处理；长材料放 `references/`，脚本放 `scripts/`。
4. 校验：

```powershell
Invoke-RestMethod "$base/api/harness/skills?cwd=<workspace>\jnf-harness" -Headers $h
Invoke-RestMethod "$base/api/harness/skills/my-skill?cwd=<workspace>\jnf-harness" -Headers $h
```

5. 让模型调用 `skill`（参数 `name=my-skill`）确认正文可加载、步骤可执行。

规范细节（frontmatter 解析行为、描述写法、正反例、自检清单）见 `skill-authoring/SKILL.md`。

## 常见问题

| 现象 | 排查 |
| --- | --- |
| 技能列表里没有 | 路径是否在某个根下、是否 `skills/<name>/SKILL.md`（或根下 `<name>.md`）、frontmatter `name` 是否 kebab-case、`description` 是否为空（空则回退正文首个非标题行，仍空则整技能跳过）、是否被禁用 |
| 加载报“技能不存在/已禁用” | 名字拼写与目录名一致；用 `GET /api/harness/skills` 确认；检查 `data/skills.properties` 里是否记录了禁用 |
| 不同 cwd 看到的技能不同 | PROJECT 根依赖 `?cwd=`；`./skills` 这类相对根依赖宿主进程工作目录 |
| 安装失败 | 看返回 message：URL 前缀不支持 / 子目录没有 SKILL.md / 同名已存在 / 含符号链接 / 超限 |
| 删除失败 | 只有用户技能目录中的技能可删；项目级、内置技能请直接改文件 |
| 正文超限 | 技能文件 >256KB 会被拒绝；拆到 `references/` |

## 安全约定

- 不放 API Key、token、私有地址；示例用 `<key>` / `env:<NAME>` / `secret:<NAME>` 占位。
- 不写作者本机绝对路径；用仓库相对路径或 `<workspace>`。
- 第三方技能视为不可信输入：审查正文与 `scripts/` 中的下载、删除、联网命令后再安装/执行。

## 说明

技能包内容以本仓库当前代码为准（`filesystem` 技能提供者、`GitSkillInstaller`、`HarnessSkillController`、`SkillService`）。若宿主行为变化，先以代码为准更新技能，再更新本 README。
