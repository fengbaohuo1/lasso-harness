# 插件 UI 清单（META-INF/plugin-ui.json）

可选能力：声明后桌面前端会渲染工具卡片、插件独立设置页、斜杠命令与 `@` 提及源。
参考实现：`plugins/sample/src/main/resources/META-INF/plugin-ui.json`；宿主读取逻辑见 `jnf-harness-trigger/src/main/java/com/jnf/harness/trigger/http/HarnessPluginController.java` 的 `contributions`。

## 与插件的关系

- 在 `META-INF/plugin.yaml` 写 `ui: META-INF/plugin-ui.json`（缺省值就是这个路径）。
- 只有 UI 清单、没有 Java 入口时，加载器允许 UI-only 插件；有 Java 入口时两者可同时存在。
- 清单内容通过 `GET /api/harness/plugins/{pluginId}/ui` 与 `GET /api/harness/plugins/{pluginId}/contributions` 暴露。

## 顶层结构

```json
{
  "schemaVersion": 1,
  "toolRenderers": {},
  "settingsPage": {},
  "slashCommands": [],
  "mentionProviders": []
}
```

各段都是可选的。

## toolRenderers：工具调用卡片

键是插件注册的工具名；值描述如何把工具结果渲染成卡片：

```json
"toolRenderers": {
  "weather_query": {
    "title": "天气查询",
    "summary": "查询城市天气",
    "blocks": [
      { "type": "kv", "label": "概览", "source": "data" },
      { "type": "code", "label": "原始 JSON", "source": "raw", "language": "json" }
    ]
  }
}
```

- `blocks[].type`：`kv`（键值）、`text`、`code`。
- `source`：数据取自结果的哪个部分（sample 中用 `data` / `raw` 等标识，前端按约定解析）。
- `language`：仅 `code` 块可选。

## settingsPage：插件独立配置页

```json
"settingsPage": {
  "title": "My Plugin 设置",
  "description": "配置上游地址与开关",
  "sections": [
    {
      "title": "连接",
      "fields": [
        { "key": "baseUrl", "label": "Base URL", "type": "text",
          "placeholder": "https://example.com", "help": "末尾不带斜杠" },
        { "key": "mode", "label": "模式", "type": "select", "default": "fast",
          "options": [ { "value": "fast", "label": "快速" }, { "value": "safe", "label": "安全" } ] },
        { "key": "retries", "label": "重试次数", "type": "number", "min": 0, "max": 10, "default": 2 },
        { "key": "enabled", "label": "启用", "type": "checkbox", "default": true },
        { "key": "notes", "label": "备注", "type": "textarea" }
      ]
    }
  ],
  "actions": [
    { "label": "测试连接", "method": "POST", "path": "test-connection", "bodyFrom": "form" }
  ]
}
```

- `fields[].type`：`text` / `select` / `number` / `checkbox` / `textarea`。
- `fields[].key` 与插件 `context.getConfig(key)` 读取的键对应；表单保存走 `PUT /api/harness/plugins/{pluginId}/config`。
- `actions[].path` 是插件端点相对路径（实际打到 `/api/harness/plugins/{pluginId}/api/<path>`）；`bodyFrom: "form"` 表示把当前表单值作为请求体。
- `actions[].method` 与 `context.registerEndpoint(method, path, ...)` 必须一致。

## slashCommands：斜杠命令

```json
"slashCommands": [
  { "name": "weekly", "description": "插入周报骨架",
    "argumentHint": "[主题]", "template": "# 周报\n- 进展：{{args}}", "mode": "insert" }
]
```

`mode` 当前使用 `insert`（把 `template` 插入输入框）。

## mentionProviders：`@` 提及源

```json
"mentionProviders": [
  { "id": "snippets", "trigger": "@", "label": "片段",
    "endpoint": "list-snippets", "readEndpoint": "read-snippet", "placeholder": "搜索片段" }
]
```

- `endpoint`：返回候选项列表的插件端点（sample 返回 `{ "items": [ { "value", "label", "description" } ] }`）。
- `readEndpoint`：按 `query.path` 返回 `{ "content": "..." }`，选中后展开为正文。
- 两个端点都用 `context.registerEndpoint("GET", ..., handler)` 注册。

## 自检

- [ ] JSON 合法，`schemaVersion` 为 1
- [ ] 每个 `settingsPage.actions[].path` 都有对应的 Java 端点
- [ ] `toolRenderers` 的键等于工具裸名（不是 `plugin__id__name` 限定名）
- [ ] `GET /api/harness/plugins/{id}/contributions` 中 `hasSettingsPage` / `hasUi` 符合预期
