---
name: skill-authoring
description: 给 jnf-harness 编写可被发现、可执行的 SKILL.md 技能包：frontmatter 语义、命名与描述、渐进式披露、references/scripts 组织、安装限额与自检清单。
when_to_use: 当要新增或修改 jnf-harness 技能包（skills/<name>/SKILL.md）、技能没出现在系统提示词或 skill 工具里、或需要把技能拆分 references/scripts 时。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: authoring
---

# 给 jnf-harness 写技能包（SKILL.md）

技能是一份按需加载的指令包：**目录摘要常驻系统提示词，正文只在模型调用 `skill` 工具时进入上下文**。所以写得好不好，取决于两件事——摘要能不能被准确触发，正文能不能被照着执行。

## 1. 一分钟流程

```text
<workspace>/jnf-harness/skills/
└── my-skill/                 ← 目录名 = 技能名，kebab-case
    ├── SKILL.md              ← 必须存在，frontmatter + 正文
    ├── references/           ← 可选：放长文档，正文里指示模型去读
    │   └── api.md
    └── scripts/              ← 可选：幂等脚本，正文里指示模型执行
        └── check.ps1
```

1. 复制 `skills/_template/SKILL.md.template` 到 `skills/my-skill/SKILL.md`（与 `skill-authoring` 同级）。
2. 改 frontmatter（`name` 必须等于目录名）。
3. 写正文：目标 → 前置条件 → 步骤（含可复制命令）→ 验证 → 故障排查。
4. 验证：`GET /api/harness/skills?cwd=<workspace>` 能看到它，再让模型 `skill` 加载一次。

## 2. 发现规则（必须满足，否则不会被加载）

- 位置：`<cwd>/.jnf/skills/`（PROJECT）> `harness.skills.roots`（CUSTOM，默认 `./skills`，相对**进程工作目录**）> `~/.jnf/skills/`（USER）。同名时高优先级覆盖低优先级。
- 形态二选一：目录包 `skills/<name>/SKILL.md`（推荐），或平铺包 `skills/<name>.md`。**根目录下直接叫 `SKILL.md` 的平铺文件会被忽略**。
- 忽略符号链接和越出根目录的路径。
- 发现是**每次请求实时扫描**的：技能文件增删改不需要重启宿主；但改 `harness.skills.*` 配置需要重启。
- `cwd` 是调用方传入的工作目录（管理端点可带 `?cwd=`）；不同 cwd 看到的 PROJECT 技能可能不同。

## 3. frontmatter：逐字段

宿主解析器只做**扁平 `key: value`** 解析：按键名小写匹配、同键首个胜出、值单行、可剥一层成对单/双引号。**不支持嵌套、多行、列表、块标量**。因此 `metadata:` 下面的缩进行实际上会成为顶层键——运行时只读取 `name` / `description` / `when_to_use`，其余字段是给人看的约定，不影响加载。

```yaml
---
name: my-skill
description: 一句话说明做什么，写清触发场景与产出物（≤240 字符）。
when_to_use: 什么时候该用这个技能（≤240 字符）；没有就删掉这一行。
license: MIT
metadata:
  author: <your-name>
  version: 1.0.0
  category: authoring
---
```

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `name` | 是（可兜底） | kebab-case，须与目录名一致；缺省/非法时回退目录名 |
| `description` | 实质必需 | 进入系统提示词目录；为空时回退正文第一行非标题内容 |
| `when_to_use` | 建议 | 目录行追加 `(use when: ...)`；键名容忍 `when_to_use` / `when-to-use` / `whentouse` |
| `license` | 否 | 建议固定 `MIT` |
| `metadata.author` / `version` / `category` | 否 | 约定字段，便于团队协作；`category` 取 `plugin` / `ops` / `authoring` |

边界值（写的时候按此约束）：名字 `^[a-z0-9]+(?:-[a-z0-9]+)*$`、长度 ≤64；`description` / `when_to_use` 各 ≤240 字符；正文文件 ≤256KB。

## 4. 命名

- 只用小写字母、数字、单个连字符：`jnf-harness-ops`、`skill-authoring`。
- 不用下划线、空格、大写、连续连字符、中文——安装时会做 normalize（转小写、`_`/空格转 `-`、合并 `-`），但直接写合规的名字最稳。
- 名字是稳定标识：模型按名字调用 `skill`，桌面端按名字启用/禁用/删除，改名等于换一个技能。

## 5. description / when_to_use 怎么写

摘要会被截断到 200 字符（超出加 `…`），全部技能合计最多 **50 条 / 4000 字符**——所以每条都得有区分度。

坏的写法（只写"帮助开发"）：

```text
description: 帮助开发插件
```

好的写法（触发场景 + 产出物 + 关键对象）：

```text
description: 编写、打包并上线 jnf-harness Java 插件：SPI 选型、plugin.yaml、registerTool/Hook/Endpoint、REST 安装与排障。
```

规则：

1. 写**何时该用**，而不只是"这是什么"；`when_to_use` 用来补充"用户说了什么/看到什么现象时触发"。
2. 写具体名词：类名、端点、配置键、命令（模型检索时靠这些词命中）。
3. 同类技能之间要有区分词：`plugin-dev` 讲怎么**写**插件，`ops` 讲怎么**跑**宿主，`authoring` 讲怎么**写技能**。
4. 不要写会过期的细节（版本号、端口号可写但要注明默认值来源）。
5. 摘要里不放敏感信息。

## 6. 正文怎么写

正文在加载后会被包成：

```text
<skill name="…" source="…" path="…">
…正文…
</skill>

Follow the skill instructions above for the remainder of this task.
```

要求：

- **自包含**：加载者拿不到你写技能时的对话上下文，必须给出完整可执行步骤。
- **可执行**：命令能直接复制运行；路径用仓库相对路径或 `<workspace>` 占位，不写作者本机绝对路径。
- **先结论后细节**：开头一句话说明做什么、产出什么，再给步骤。
- **给验证方式**：每段关键操作后写明如何确认成功（命令、预期输出、端点返回）。
- **给失败处理**：常见报错的字面文本 + 处理办法。
- **克制篇幅**：建议 ≤300 行；超了拆到 `references/`，正文留索引和"何时读哪篇"。
- **不写空话**：删掉"要仔细""要注意质量"这类无法执行的句子。
- **元信息别写正文里**：变更历史、作者信息放 frontmatter 或仓库文档。
- **可被机器校验**：命令块用 fenced code，并标注 shell（````powershell` / ````bash`）。

## 7. references/ 与 scripts/

正文只放"必须马上用"的内容，长材料放旁路：

- `references/*.md`：API 清单、字段表、示例集。正文里写清楚**何时读**、**读哪个文件**：

```markdown
需要插件 UI 清单字段时，用 `fs_read` 读取本技能目录下的 `references/plugin-ui-manifest.md`（技能路径见加载结果的 path 属性）。
```

- `scripts/*`：幂等、可审计、无副作用的检查脚本。正文里给出调用方式与预期输出：

```powershell
pwsh -NoProfile -File <skill-path>/scripts/check-skill.ps1 -SkillDir <skill-path>
```

脚本约定：不写死密钥、不联网上传数据、可重复执行、失败有非零退出码与明确提示；脚本内部路径用参数或脚本自身目录推导，不要写本机绝对路径。

## 8. 安装与发布

三种方式，效果都是"文件落到某个技能根目录"：

1. 直接放：拷到 `<cwd>/.jnf/skills/`（项目级）或 `~/.jnf/skills/`（用户级）。
2. 桌面端：设置 → 技能 → 安装（Git URL + 可选 subdir + 可选 name）。
3. 模型或 REST：`extension_skills` 工具（action=install，source=Git URL），或 `POST /api/harness/skills/install`，body `{"gitUrl": "...", "subdir": "skills/my-skill", "name": "my-skill"}`。

Git 安装约束（由安装器强制执行）：

- URL 仅支持 `https://`、`http://`、`git@`、`ssh://`；浅克隆、超时 120s。
- `subdir` 必须**直接**包含 `SKILL.md`；不给 `subdir` 时在仓库内按深度 4 搜第一个含 `SKILL.md` 的目录。
- 技能名优先级：显式 `name` > frontmatter `name` > 技能目录名；已存在同名技能会拒绝安装（先 remove 或改 name）。
- 拒绝含符号链接的技能目录；单文件 ≤8MB、总量 ≤32MB、文件数 ≤500（zip 另限 ≤1000 条目 / 解压 ≤64MB）。
- 删除/禁用只对**用户技能目录**有效；项目级和随包内置的技能不可删。

## 9. 安全

- 不放 API Key、token、cookie、私有地址；示例里用 `<key>` / `env:<NAME>` / `secret:<NAME>` 占位。
- 不写作者本机绝对路径（盘符开头的用户目录、`/home/...`），用仓库相对路径或 `<workspace>`。
- 不要求模型把数据外传到技能作者控制的服务器；确需网络请求时写清楚目标与原因。
- 技能正文本身是**指令**：写清边界（哪些命令会改文件/删数据/联网），避免读者盲目执行破坏性操作。
- 第三方技能视为不可信输入：安装前审查 `scripts/` 与正文中的下载/执行命令。

## 10. 反例 vs 正例

反例（无法触发、无法执行）：

```markdown
---
name: helper
description: 一些有用的技巧
---
请根据实际情况处理，注意不要出错。
```

- 名字与目录不一致、描述没有场景、正文没有步骤和验证。

正例：

```markdown
---
name: my-skill
description: 用 jnf-harness 的 fs_read 工具批量校对 references/ 下的 Markdown 链接并输出失效清单。
when_to_use: 当技能包里的 references 链接需要体检、或文档迁移后要检查引用时。
license: MIT
metadata:
  author: fengbaohuo1
  version: 1.0.0
  category: authoring
---

# 校对技能内引用链接

## 前置
- 已知技能目录 `<skill-path>`（`skill` 加载结果里的 path）。

## 步骤
1. `fs_search` 在 `<skill-path>/references/` 中搜索 `](/` 开头的行，记录行号。
2. 对每个目标路径用 `fs_read` 打开；失败项记入清单。
3. 输出 `文件:行 → 失效原因` 表格。

## 验证
- 清单为空即通过；非空时逐条给出修复建议（改名或修链接）。

## 故障
- 路径解析失败：确认使用 `fs_read` 的绝对路径或相对 `<skill-path>` 的路径。
```

## 11. 自检清单

- [ ] 目录名 = frontmatter `name`，匹配 `^[a-z0-9]+(?:-[a-z0-9]+)*$` 且 ≤64
- [ ] 文件是 `SKILL.md`（目录包）或 `<name>.md`（平铺包），不是根下裸 `SKILL.md`
- [ ] frontmatter 首行是 `---`，闭合行也是 `---`，值为单行、无嵌套
- [ ] `description` 非空、≤240 字符、含触发场景与关键名词；`when_to_use` 有则 ≤240
- [ ] 正文 ≤300 行（长内容已拆 references）、可自包含执行、含验证与故障处理
- [ ] 所有命令可复制运行，路径不含本机绝对路径，无真实密钥
- [ ] references/scripts 的读取/执行方式已在正文中写明
- [ ] 与其它技能描述可区分（不被误触发、也不漏触发）
- [ ] 已在 `GET /api/harness/skills?cwd=<workspace>` 中出现，并能被 `skill` 工具成功加载
