# AI 面板实现指南（浏览器 SSE 消费 + 服务端代理）

应用里的 AI 助手 = **一个浮层 + 一条 SSE 流**。浏览器只访问应用自己的 `/api/assistant/stream`，由应用服务端代理到底座 `POST /api/agent/stream`。
下面代码可直接抄进应用，零依赖、离线可跑。

## 1. 约定与载荷形状

```text
[AI 面板] --POST /api/assistant/stream--> [应用代理] --POST /api/agent/stream--> [harness]
          <--------- SSE 逐帧透传 ---------          <----- SSE 原始字节 -------
```

事件名（底座 `AgentStreamApi` 映射）：`meta`、`chunk`、`thinking`、`step_break`、`tool_result`、`usage`、`finish`、`done`、`error`。
帧格式 `event: <name>\ndata: <json>\n\n`；每 15s 一条 `:keepalive` 注释帧。

| 事件 | 载荷 | 注意 |
| --- | --- | --- |
| `meta` | `{agentId}` | 记录会话 id |
| `chunk` / `thinking` | `{content}` | `thinking` 可选折叠展示 |
| `step_break` | `{toolName, callId, args, status}` | `args` 是 **JSON 字符串**；**可能重复推送** |
| `tool_result` | `{toolName, callId, result, error}` | `result` 是 **JSON 字符串**；`error` 是 **布尔** |
| `usage` | `{promptTokens, completionTokens, totalTokens, cachedTokens, cacheHitRate, llmCalls, elapsedMs, tokensPerSecond, lastPromptTokens}` | 可选显示 |
| `finish` / `done` | `finish{turn}`；`done{agentId, turn, status, text, toolCalls}` | `toolCalls` 是**数量**（数字）；以 `done.text` 为准 |
| `error` | `{code, message}` | 握手后无法改 HTTP 码，只能靠这个事件 |

> 桌面端 `jnf-desktop/src/types.ts` 把 `done.toolCalls` 声明为数组、`args` 声明为对象，与线上不一致——**以 SSE 实际数据为准**，解析时兼容字符串/对象两种。

## 2. 浏览器端 SSE 消费

```javascript
function parseSseFrame(raw) {
  let event = 'message';
  const dataLines = [];
  for (const line of raw.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue;      // 空行 / keepalive
    const i = line.indexOf(':');
    const field = i === -1 ? line : line.slice(0, i);
    let value = i === -1 ? '' : line.slice(i + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') dataLines.push(value);
  }
  if (!dataLines.length) return null;
  try { return { event, payload: JSON.parse(dataLines.join('\n')) }; }
  catch { return null; }                               // 非 JSON 帧丢弃
}

async function streamAssistant({ message, signal, onEvent }) {
  const resp = await fetch('/api/assistant/stream', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }), signal,
  });
  if (!resp.ok || !resp.body) {                        // 握手前失败给 JSON 信封
    const text = await resp.text().catch(() => '');
    throw new Error('AI 服务不可用：' + (text.slice(0, 120) || resp.status));
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      for (;;) {                                       // 按空行切帧，不是按换行
        const m = /\r?\n\r?\n/.exec(buffer);
        if (!m) break;
        const frame = parseSseFrame(buffer.slice(0, m.index));
        buffer = buffer.slice(m.index + m[0].length);
        if (frame) onEvent(frame.event, frame.payload);
      }
    }
    if (buffer.trim()) {                               // 流末尾不完整帧
      const frame = parseSseFrame(buffer);
      if (frame) onEvent(frame.event, frame.payload);
    }
  } finally { reader.releaseLock?.(); }
}
```

- 不要用 `EventSource`（只能 GET、不能带 body / Header）；建议另设 120s 空闲看门狗（每次 `read()` 重置，超时 `abort()` 并提示重试）；`AbortController.signal` 传给 `fetch`，「停止」按钮才能真断流。

## 3. 事件 → 面板状态

```javascript
const state = { answer: '', tools: new Map(), done: false, error: null, usage: null };

function onEvent(event, payload) {
  switch (event) {
    case 'meta': state.agentId = payload.agentId; break;
    case 'chunk': state.answer += payload.content || ''; renderAnswer(); break;   // 每 chunk 重渲染
    case 'thinking': break;                             // 可选折叠，不并入 answer
    case 'step_break':
      if (state.tools.has(payload.callId)) break;       // ← 去重：重复帧不改状态
      state.tools.set(payload.callId, { toolName: payload.toolName, args: safeParse(payload.args),
        result: null, error: false, status: payload.status || 'running' });
      renderTools(); break;
    case 'tool_result': {
      const card = state.tools.get(payload.callId) || { toolName: payload.toolName, args: null };
      card.result = safeParse(payload.result);
      card.error = payload.error === true || payload.error === 'true';
      card.status = card.error ? 'failed' : 'ok';
      state.tools.set(payload.callId, card);            // 兼容缺 step_break 的情况
      renderTools(); break;
    }
    case 'usage': state.usage = payload; break;
    case 'finish': break;                               // 一轮结束，不要在这里关流
    case 'done': state.answer = payload.text || state.answer; state.done = true; setLoading(false); renderAnswer(); break;
    case 'error': state.error = { code: payload.code, message: payload.message }; setLoading(false); renderError(); break;
  }
}
function safeParse(v) {
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return v; }    // 普通文本原样展示
}
```

工具轨迹渲染：每张卡显示工具名 + 入参摘要（`args` 里取 2~3 个关键字段），`tool_result` 后补「成功/失败」；原始 JSON 放折叠区，用 `textContent` 写入 `<pre>`，不拼 `innerHTML`。

## 4. Markdown 渲染（先转义，流式重渲染）

模型输出是 markdown，**禁止 `textContent` 裸显**（用户会看到满屏 `**`）。先 `escapeHtml` 再拼 HTML；用户气泡保持 `textContent`。

```javascript
function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
                  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function inlineMd(s) {
  return s.replace(/`([^`]+)`/g, '<code>$1</code>')
          .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
          .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
          .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
            '<a href="$2" target="_blank" rel="noopener">$1</a>');
}
function renderMd(src) {
  const lines = escapeHtml(src).split('\n');
  let html = '', list = [], listType = '', inCode = false, code = [];
  const flushList = () => {
    if (!list.length) return;
    html += `<${listType || 'ul'}>` + list.map(i => '<li>' + inlineMd(i) + '</li>').join('') + `</${listType || 'ul'}>`;
    list = []; listType = '';
  };
  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '');
    if (/^```/.test(line)) {                           // 代码块
      if (inCode) { html += '<pre class="code"><code>' + code.join('\n') + '</code></pre>'; inCode = false; code = []; }
      else { flushList(); inCode = true; }
      continue;
    }
    if (inCode) { code.push(raw); continue; }
    const li = line.match(/^\s*[-*•]\s+(.*)$/);
    if (li) { if (listType === 'ol') flushList(); listType = 'ul'; list.push(li[1]); continue; }
    const oli = line.match(/^\s*\d+[.)]\s+(.*)$/);
    if (oli) { if (listType === 'ul') flushList(); listType = 'ol'; list.push(oli[1]); continue; }
    flushList();
    if (!line.trim()) continue;
    const h = line.match(/^(#{1,4})\s+(.*)$/);
    if (h) { const lv = Math.min(h[1].length + 2, 5); html += `<h${lv}>` + inlineMd(h[2]) + `</h${lv}>`; continue; }
    html += '<p>' + inlineMd(line.trim()) + '</p>';
  }
  if (inCode) html += '<pre class="code"><code>' + code.join('\n') + '</code></pre>';
  flushList();
  return html || '<p></p>';
}

function renderAnswer() {
  const el = document.querySelector('#ai-msg-answer');
  el.classList.add('md');
  el.innerHTML = state.answer ? renderMd(state.answer) : '<span class="typing">正在想…</span>';
}
```

```css
.msg.ai .bubble.md { white-space: normal; }            /* pre-wrap 只留给纯文本兜底 */
.md p { margin: 0 0 8px; } .md > :last-child { margin-bottom: 0; }
.md ul, .md ol { margin: 2px 0 8px; padding-left: 20px; }
.md h3, .md h4, .md h5 { margin: 10px 0 4px; font-weight: 600; }
.md code { background: rgba(0,0,0,.07); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.md pre.code { background: #1f2328; color: #e6edf3; padding: 12px; border-radius: 10px; overflow: auto; }
```

三条铁律：① 先 `escapeHtml` 再拼 HTML（防模型输出注入 XSS）；② **流式逐 chunk 重渲染**，不等结束；③ 不引 CDN 库（marked 等），离线可跑。

## 5. 面板交互（浮动按钮 + 侧滑）

结构：`<button id="ai-fab">` 浮钮 + `<aside id="ai-panel" hidden>` 侧滑（`header` 关闭按钮 / `.ai-log` 消息区，AI 气泡是 `#ai-msg-answer` / `#ai-tools` 工具轨迹 / `#ai-examples` 示例胶囊 / `form` 输入 + 发送 + 停止）。中文占位文案写进 HTML，不要运行时拼接。

```javascript
let controller = null;
async function send(message) {
  state.answer = ''; state.tools.clear(); state.error = null; state.done = false;
  renderAnswer(); renderTools(); setLoading(true);
  controller = new AbortController();
  try {
    await streamAssistant({ message, signal: controller.signal,
      onEvent: (event, payload) => onEvent(event, payload) });
  } catch (err) {
    appendNotice(err.name === 'AbortError' ? '已停止生成' : '出错了：' + err.message);
  } finally { controller = null; setLoading(false); }
}

// 示例问题：点一下即发送，每条注明会触发的工具（写进 title）
const EXAMPLES = [
  { text: '这个月哪类支出最高？', tool: 'category_summary' },
  { text: '帮我看下 A 项目的进度和风险', tool: 'task_detail + member_workload' },
  { text: '把工单 T-1024 标记为已处理', tool: 'ticket_update（写）' },
];
```

- 面板：固定右侧 380px、圆角 24px、毛玻璃底；移动端 `width:100%`；全局兜底 `[hidden]{display:none!important}`；
- 浮钮 56px 圆形，用本应用主色渐变（跟随 `references/ui-design-guide.md` 的设计语言）；
- 发送中禁用输入并显示「停止」；`error` 用面板内红色提示条，**不用 `alert()`**；
- 关闭方式：关闭按钮 + 遮罩点击 + Esc 三种都要支持。

## 6. 服务端代理 `/api/assistant/stream`（Spring Boot）

职责只有四件：**参数白名单 → 注入底座地址/Key → 透传 SSE → 失败给信封**。不要解析或改写 SSE。

```java
@RestController
@RequestMapping("/api/assistant")
public class AssistantProxyController {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl, apiKey, defaultAgentId;

    public AssistantProxyController(
            @Value("${harness.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${harness.api-key:}") String apiKey,
            @Value("${harness.agent-id:app-assistant}") String defaultAgentId) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.defaultAgentId = defaultAgentId;
    }

    @PostMapping("/stream")
    public void stream(@RequestBody Map<String, Object> body, HttpServletResponse resp) throws IOException {
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message")).trim();
        if (message.isEmpty()) { writeEnvelope(resp, "40000", "message 不能为空"); return; }
        // 白名单：agentId / message / cwd；模型等参数由服务端统一口径，不转发
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", text(body.get("agentId"), defaultAgentId));
        payload.put("message", message);
        payload.put("cwd", text(body.get("cwd"), System.getProperty("user.dir")));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/agent/stream"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8));
        if (!apiKey.isBlank()) builder.header("X-API-Key", apiKey);
        HttpResponse<InputStream> upstream;
        try {
            upstream = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            writeEnvelope(resp, "50000", "无法连接 AI 服务：" + e.getClass().getSimpleName());
            return;
        }
        if (upstream.statusCode() != 200) {
            try (InputStream ignored = upstream.body()) { }
            writeEnvelope(resp, "50000", "AI 服务返回 HTTP " + upstream.statusCode());
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/event-stream;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");           // 防网关攒帧
        try (InputStream in = upstream.body(); OutputStream out = resp.getOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); out.flush(); }
        } catch (IOException clientGone) {
            // 浏览器已断开：关闭上游即可，不要重试
        }
    }

    private void writeEnvelope(HttpServletResponse resp, String code, String info) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);            // 应用信封约定：HTTP 恒 200
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(MAPPER.writeValueAsString(Map.of("code", code, "info", info)));
    }
    private static String text(Object v, String dft) {
        return v == null || String.valueOf(v).isBlank() ? dft : String.valueOf(v);
    }
}
```

代理检查点：
- API Key 只读服务端配置，**永不出现在前端代码、页面源码、日志**；上游非 200（如底座 401）转 `50000` 信封，前端提示「AI 服务未就绪」而不是空白面板；
- `agentId` 由服务端兜底，前端不能指定任意 agent；浏览器 `abort()` 后上游可能仍在跑，若后续请求报「正在执行上一条消息」，说明底座 Agent 卡 RUNNING，重启底座即可。

## 7. 上线前自测（不用浏览器也能验）

```powershell
# 代理通、SSE 有内容（能看到 event: meta / chunk / done）
curl.exe -N -s -X POST http://127.0.0.1:18081/api/assistant/stream -H "Content-Type: application/json" -d '{\"message\":\"你好\"}'
# 直连底座对照（代理输出应与之一致）
curl.exe -N -s -X POST http://127.0.0.1:8090/api/agent/stream -H "Content-Type: application/json" -d '{\"agentId\":\"app-assistant\",\"message\":\"你好\"}'
```

浏览器再点一遍：浮层开关、逐字输出、工具卡只出现一次、markdown 排版正常、停止可用、错误提示可读、375px 不塌。
