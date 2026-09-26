/* jnf-harness Web 控制台（原生 JS，无构建链） */
(function () {
  'use strict';

  var messagesEl = document.getElementById('messages');
  var inputEl = document.getElementById('input');
  var sendBtn = document.getElementById('sendBtn');
  var cancelBtn = document.getElementById('cancelBtn');
  var agentIdEl = document.getElementById('agentId');
  var apiKeyEl = document.getElementById('apiKey');
  var approvalsEl = document.getElementById('approvals');
  var sessionSelect = document.getElementById('sessionSelect');
  var replayBtn = document.getElementById('replayBtn');
  var replayStatus = document.getElementById('replayStatus');

  var abortController = null;

  apiKeyEl.value = localStorage.getItem('jnf-api-key') || '';
  apiKeyEl.addEventListener('change', function () {
    localStorage.setItem('jnf-api-key', apiKeyEl.value.trim());
  });

  function headers(json) {
    var h = {};
    if (json) { h['Content-Type'] = 'application/json'; }
    if (apiKeyEl.value.trim()) { h['X-API-Key'] = apiKeyEl.value.trim(); }
    return h;
  }

  function addBubble(role, text) {
    var div = document.createElement('div');
    div.className = 'msg ' + role;
    div.textContent = text;
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return div;
  }

  function addToolCard(toolName, args) {
    var card = document.createElement('div');
    card.className = 'tool-card';
    card.innerHTML = '<div class="name">🔧 ' + escapeHtml(toolName) + '</div>' +
      '<div class="detail">' + escapeHtml(args || '') + '</div>';
    messagesEl.appendChild(card);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return card;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  /* ---------------- SSE 对话 ---------------- */

  function sendMessage() {
    var text = inputEl.value.trim();
    if (!text || abortController) { return; }
    var agentId = agentIdEl.value.trim() || 'console-agent';
    addBubble('user', text);
    inputEl.value = '';
    sendBtn.disabled = true;
    cancelBtn.disabled = false;

    abortController = new AbortController();
    var assistantBubble = null;
    var toolCards = {};

    fetch('/api/agent/stream', {
      method: 'POST',
      headers: headers(true),
      body: JSON.stringify({ agentId: agentId, message: text }),
      signal: abortController.signal
    }).then(function (response) {
      if (!response.ok) { throw new Error('HTTP ' + response.status); }
      var reader = response.body.getReader();
      var decoder = new TextDecoder();
      var buffer = '';

      function pump() {
        return reader.read().then(function (result) {
          if (result.done) { return; }
          buffer += decoder.decode(result.value, { stream: true });
          var frames = buffer.split('\n\n');
          buffer = frames.pop();
          frames.forEach(handleFrame);
          return pump();
        });
      }

      function handleFrame(frame) {
        var event = '';
        var data = '';
        frame.split('\n').forEach(function (line) {
          if (line.indexOf('event:') === 0) { event = line.slice(6).trim(); }
          if (line.indexOf('data:') === 0) { data += line.slice(5).trim(); }
        });
        if (!event || !data) { return; }
        var payload;
        try { payload = JSON.parse(data); } catch (e) { return; }

        if (event === 'chunk') {
          if (!assistantBubble) { assistantBubble = addBubble('assistant', ''); }
          assistantBubble.textContent += payload.content;
          messagesEl.scrollTop = messagesEl.scrollHeight;
        } else if (event === 'step_break') {
          toolCards[payload.callId] = addToolCard(payload.toolName, payload.args);
        } else if (event === 'tool_result') {
          var card = toolCards[payload.callId];
          if (card) {
            card.className = 'tool-card ' + (payload.error ? 'error' : 'success');
            card.querySelector('.detail').textContent =
              (payload.error ? '✕ ' : '✓ ') + String(payload.result || '').slice(0, 800);
          }
        } else if (event === 'error') {
          addBubble('error', '错误[' + payload.code + ']: ' + payload.message);
        }
      }

      return pump();
    }).catch(function (err) {
      if (err.name !== 'AbortError') {
        addBubble('error', '连接失败: ' + err.message);
      }
    }).then(function () {
      abortController = null;
      sendBtn.disabled = false;
      cancelBtn.disabled = true;
    });
  }

  sendBtn.addEventListener('click', sendMessage);
  inputEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      sendMessage();
    }
  });

  cancelBtn.addEventListener('click', function () {
    if (abortController) { abortController.abort(); }
    fetch('/api/agent/' + encodeURIComponent(agentIdEl.value.trim() || 'console-agent') + '/cancel', {
      method: 'POST', headers: headers(false)
    }).catch(function () {});
  });

  /* ---------------- 运行期审批 ---------------- */

  function renderApprovals(list) {
    if (!list || !list.length) {
      approvalsEl.innerHTML = '<p class="muted">（无待审批）</p>';
      return;
    }
    approvalsEl.innerHTML = '';
    list.forEach(function (item) {
      var div = document.createElement('div');
      div.className = 'approval-item';
      div.innerHTML = '<div><b>' + escapeHtml(item.toolName) + '</b> 请求审批</div>' +
        '<div class="detail muted">' + escapeHtml(item.argsJson || '') + '</div>' +
        '<div class="actions">' +
        '<button data-id="' + escapeHtml(item.id) + '" data-v="ALLOW_ONCE">允许一次</button>' +
        '<button class="danger" data-id="' + escapeHtml(item.id) + '" data-v="DENY">拒绝</button>' +
        '</div>';
      approvalsEl.appendChild(div);
    });
    approvalsEl.querySelectorAll('button[data-id]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        fetch('/api/harness/approvals/runtime/' + btn.dataset.id + '/resolve', {
          method: 'POST', headers: headers(true),
          body: JSON.stringify({ verdict: btn.dataset.v })
        }).then(pollApprovals).catch(function () {});
      });
    });
  }

  function pollApprovals() {
    fetch('/api/harness/approvals/runtime/pending', { headers: headers(false) })
      .then(function (r) { return r.json(); })
      .then(function (envelope) {
        renderApprovals(envelope && envelope.data ? envelope.data : []);
      })
      .catch(function () {});
  }

  /* ---------------- 历史回放 ---------------- */

  function loadSessions() {
    fetch('/api/harness/console/sessions', { headers: headers(false) })
      .then(function (r) { return r.json(); })
      .then(function (envelope) {
        var list = (envelope && envelope.data) || [];
        sessionSelect.innerHTML = '<option value="">-- 选择会话 --</option>';
        list.forEach(function (s) {
          var opt = document.createElement('option');
          opt.value = s.sessionId;
          opt.textContent = s.sessionId + '（' + s.events + ' 事件）';
          sessionSelect.appendChild(opt);
        });
      })
      .catch(function () {});
  }

  replayBtn.addEventListener('click', function () {
    var sessionId = sessionSelect.value;
    if (!sessionId) { return; }
    replayStatus.textContent = '回放中…';
    fetch('/api/harness/console/sessions/' + encodeURIComponent(sessionId) + '/messages',
      { headers: headers(false) })
      .then(function (r) { return r.json(); })
      .then(function (envelope) {
        messagesEl.innerHTML = '';
        (envelope.data || []).forEach(function (m) {
          if (m.blocks && m.blocks.length) {
            m.blocks.forEach(function (b) {
              if (b.type === 'tool_call') { addToolCard(b.name, b.arguments); }
            });
          }
          if (m.text) {
            addBubble(m.role === 'USER' ? 'user' : (m.role === 'SYSTEM' ? 'error' : 'assistant'), m.text);
          }
        });
        replayStatus.textContent = '回放完成（' + (envelope.data || []).length + ' 条）';
      })
      .catch(function (e) { replayStatus.textContent = '回放失败: ' + e.message; });
  });

  /* ---------------- 初始化轮询 ---------------- */

  pollApprovals();
  setInterval(pollApprovals, 3000);
  loadSessions();
})();
