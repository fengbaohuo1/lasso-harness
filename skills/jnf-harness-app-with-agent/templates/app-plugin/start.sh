#!/usr/bin/env bash
# ============================================================================
# 独立产品一键启动（Linux/macOS 骨架，与 start.ps1 逻辑等价）
#
# 顺序：校验 jar → 端口检查 → 起 harness → /actuator/health=UP
#       → install/activate 插件 → PUT 插件配置 → 校验 contributions
#       → 起应用 → 首页 200 → 写 data/runtime.json → 打印地址。
# 幂等：harness/应用已健康则复用，不重复起进程。
#
# 用法：chmod +x scripts/start.sh && scripts/start.sh
# 环境变量：PRODUCT_ROOT / HARNESS_PORT / APP_PORT / API_KEY（也可直接改下面默认值）
#
# 注意：本骨架通过 bash -n 语法检查，但尚未在 Linux/macOS 实跑完整链路；
#      首次使用请按输出逐步验证，跑通后把「已实跑」写进 README。
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="${PRODUCT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
HARNESS_PORT="${HARNESS_PORT:-18100}"
APP_PORT="${APP_PORT:-18081}"
API_KEY="${API_KEY:-}"

# ===== 本产品参数（换项目时改这里，与 start.ps1 保持一致）=====
PLUGIN_ID="demo-bridge"
PLUGIN_ENTRYPOINT="com.example.appplugin.plugin.DemoBridgePlugin"
PLUGIN_VERSION="1.0.0"
HARNESS_JAR="$PRODUCT_ROOT/runtime/jnf-harness-app.jar"
PLUGIN_JAR="$PRODUCT_ROOT/runtime/plugins/sample-plugin.jar"
APP_JAR="$PRODUCT_ROOT/runtime/app/sample-app.jar"
DATA_DIR="$PRODUCT_ROOT/data"
LOG_DIR="$PRODUCT_ROOT/logs"
RUNTIME_JSON="$DATA_DIR/runtime.json"
HARNESS_BASE="http://127.0.0.1:$HARNESS_PORT"
APP_BASE="http://127.0.0.1:$APP_PORT"

HARNESS_PID=""
APP_PID=""
HARNESS_REUSED="false"
APP_REUSED="false"

fail() { printf '[FAIL] %s\n' "$*" >&2; exit 1; }
info() { printf '[INFO] %s\n' "$*"; }
step() { printf '[STEP] %s\n' "$*"; }
ok() { printf '[ OK ] %s\n' "$*"; }
skip() { printf '[SKIP] %s\n' "$*"; }

# 本机探活必须绕代理：HTTP_PROXY 会把 127.0.0.1 请求转给代理，得到 502/000。
curl_base() { curl -fsS --noproxy '*' --max-time 3 "$@"; }
harness_health() {
  curl_base -H "X-API-Key: $API_KEY" "$HARNESS_BASE/actuator/health" 2>/dev/null | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'
}
app_health() { curl_base -o /dev/null "$APP_BASE/" 2>/dev/null; }

# /dev/tcp 是 bash 内建，不依赖 ss/netstat；连接成功即端口在听。
port_listening() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

# 启动失败时把日志尾巴打到 stderr（终端里能直接看到根因）
show_tail() {
  if [ -f "$1" ]; then tail -n 15 "$1" >&2 || true; fi
}

wait_for() {
  local desc="$1" timeout="$2"
  shift 2
  local i=0
  while [ "$i" -lt "$timeout" ]; do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep 1
    i=$((i + 1))
  done
  printf '       等待「%s」超时（%ss）\n' "$desc" "$timeout" >&2
  return 1
}

# 所有 harness 管理端点统一走这里：带 API Key，-f 让 HTTP 4xx/5xx 直接失败。
api() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl_base -X "$method" "$HARNESS_BASE$path" -H "Content-Type: application/json" -d "$body"
  else
    curl_base -X "$method" "$HARNESS_BASE$path"
  fi
}
assert_ok() {
  local what="$1" resp="$2"
  printf '%s' "$resp" | grep -q '"code":"00000"' || fail "$what 失败：$(printf '%s' "$resp" | head -c 300)"
}

# ===== 0. 校验 =====
step '0/6 校验产品目录与运行产物'
[ -d "$PRODUCT_ROOT" ] || fail "产品目录不存在：$PRODUCT_ROOT"
command -v java >/dev/null 2>&1 || fail '找不到 java（需要 JDK 17+）：装 temurin-17-jdk 后重开终端'
[ "$HARNESS_PORT" != "$APP_PORT" ] || fail "harness 端口与应用端口不能相同（$HARNESS_PORT）"

missing=""
[ -f "$HARNESS_JAR" ] || missing="$missing
  - runtime/jnf-harness-app.jar（在 <harness> 里 mvn -o -B -DskipTests package 后复制，或复制 dist/jnf-harness-local/）"
[ -f "$PLUGIN_JAR" ] || missing="$missing
  - runtime/plugins/sample-plugin.jar（插件模块 mvn -o -B -DskipTests package 后复制）"
[ -f "$APP_JAR" ] || missing="$missing
  - runtime/app/sample-app.jar（应用模块 mvn -o -B -DskipTests package 后复制）"
[ -z "$missing" ] || fail "缺少运行产物：$missing"

mkdir -p "$DATA_DIR/harness" "$DATA_DIR/events" "$LOG_DIR"

# API Key：环境变量 > runtime.json 复用 > 新生成（32 位 hex，写入 runtime.json 供下次复用）
if [ -z "$API_KEY" ] && [ -f "$RUNTIME_JSON" ]; then
  API_KEY="$(grep -o '"apiKey"[[:space:]]*:[[:space:]]*"[^"]*"' "$RUNTIME_JSON" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')" || true
  [ -z "$API_KEY" ] || info '复用 data/runtime.json 中的 API Key'
fi
if [ -z "$API_KEY" ]; then
  if command -v uuidgen >/dev/null 2>&1; then
    API_KEY="$(uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]')"
  else
    API_KEY="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
  fi
  info '已生成新的 harness API Key（写入 data/runtime.json；该文件含密钥，勿外传）'
fi

# ===== 1. harness =====
step "1/6 启动 harness（端口 $HARNESS_PORT）"
if harness_health; then
  skip "harness 已健康，复用 $HARNESS_BASE"
  HARNESS_REUSED="true"
  HARNESS_PID=""
else
  if port_listening "$HARNESS_PORT"; then
    fail "端口 $HARNESS_PORT 已被占用但健康检查不通过：换端口 HARNESS_PORT=18120，或定位占用进程（ss -ltnp | grep :$HARNESS_PORT）后手工处理"
  fi
  DS_URL="jdbc:h2:file:$PRODUCT_ROOT/data/harness/jnf-harness;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
  info "java -jar $HARNESS_JAR --server.port=$HARNESS_PORT ...（日志 $LOG_DIR/harness.log）"
  nohup java -jar "$HARNESS_JAR" \
    --server.port="$HARNESS_PORT" \
    --spring.datasource.url="$DS_URL" \
    --harness.events.dir="$DATA_DIR/events" \
    --harness.auth.api-keys="$API_KEY" \
    >"$LOG_DIR/harness.log" 2>"$LOG_DIR/harness.err.log" &
  HARNESS_PID=$!
  info "harness PID=$HARNESS_PID，等待健康检查（最长 90s）..."
  if ! wait_for 'harness /actuator/health=UP' 90 harness_health; then
    show_tail "$LOG_DIR/harness.err.log"
    fail "harness 启动失败（PID $HARNESS_PID 可能仍在启动中）：看 $LOG_DIR/harness.log / harness.err.log"
  fi
fi
ok "harness UP：$HARNESS_BASE"

# ===== 2. 插件 install / activate =====
step "2/6 安装并激活插件 $PLUGIN_ID"
install_body=$(printf '{"pluginId":"%s","displayName":"%s","pluginVersion":"%s","runtimeType":"JAVA_NATIVE","sourcePath":"%s","entrypoint":"%s"}' \
  "$PLUGIN_ID" "$PLUGIN_ID" "$PLUGIN_VERSION" "$PLUGIN_JAR" "$PLUGIN_ENTRYPOINT")
resp=$(api POST /api/harness/plugins/install "$install_body") || fail '登记插件失败：检查 API Key 与 harness 日志'
assert_ok '登记插件' "$resp"
resp=$(api POST /api/harness/plugins/activate "{\"pluginId\":\"$PLUGIN_ID\"}") || fail '激活插件失败'
assert_ok '激活插件' "$resp"
ok '插件已 ACTIVE'

# ===== 3. 插件配置 + contributions =====
step '3/6 写插件配置（应用地址 + 插件数据目录）'
mkdir -p "$DATA_DIR/$PLUGIN_ID"
config_body=$(printf '{"app.base-url":"http://127.0.0.1:%s","dataDir":"%s"}' "$APP_PORT" "$DATA_DIR/$PLUGIN_ID")
resp=$(api PUT "/api/harness/plugins/$PLUGIN_ID/config" "$config_body") || fail '写插件配置失败'
assert_ok '写插件配置' "$resp"
resp=$(api GET "/api/harness/plugins/$PLUGIN_ID/contributions") || fail '读取 contributions 失败'
assert_ok '读取 contributions' "$resp"
printf '%s' "$resp" | grep -q '"tools":\[{' || fail '插件 ACTIVE 但没有任何工具贡献：检查 configure() 是否先 super.configure(context)'
ok '工具贡献已注册'

# ===== 4. 应用 =====
step "4/6 启动应用（端口 $APP_PORT）"
if app_health; then
  skip "应用已可访问，复用 $APP_BASE/"
  APP_REUSED="true"
  APP_PID=""
else
  if port_listening "$APP_PORT"; then
    fail "端口 $APP_PORT 已被占用但应用不可访问：换 APP_PORT=18082"
  fi
  nohup java -jar "$APP_JAR" \
    --server.port="$APP_PORT" \
    --harness.base-url="$HARNESS_BASE" \
    --harness.api-key="$API_KEY" \
    >"$LOG_DIR/app.log" 2>"$LOG_DIR/app.err.log" &
  APP_PID=$!
  info "应用 PID=$APP_PID，等待首页 200（最长 90s）..."
  if ! wait_for '应用首页 200' 90 app_health; then
    show_tail "$LOG_DIR/app.err.log"
    fail "应用启动失败（PID $APP_PID）：看 $LOG_DIR/app.log / app.err.log"
  fi
fi
ok "应用可访问：$APP_BASE/"

# ===== 5. 写 runtime.json（stop.sh 与下次 start 依赖它）=====
step '5/6 写 data/runtime.json'
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$RUNTIME_JSON" <<JSON
{
  "productRoot": "$PRODUCT_ROOT",
  "status": "RUNNING",
  "startedAt": "$STARTED_AT",
  "apiKey": "$API_KEY",
  "harness": { "port": $HARNESS_PORT, "baseUrl": "$HARNESS_BASE", "pid": ${HARNESS_PID:-null}, "reused": $HARNESS_REUSED, "jar": "runtime/jnf-harness-app.jar", "log": "logs/harness.log" },
  "app": { "port": $APP_PORT, "baseUrl": "$APP_BASE", "pid": ${APP_PID:-null}, "reused": $APP_REUSED, "jar": "runtime/app/sample-app.jar", "log": "logs/app.log" },
  "plugin": { "id": "$PLUGIN_ID", "jar": "runtime/plugins/sample-plugin.jar", "entrypoint": "$PLUGIN_ENTRYPOINT", "version": "$PLUGIN_VERSION" }
}
JSON
ok "已写入 $RUNTIME_JSON（含 API Key，勿提交/外传）"

# ===== 6. 完成 =====
step '6/6 启动完成'
printf '\n'
printf '  应用：    %s/          （业务界面 + 右下角 AI 助手）\n' "$APP_BASE"
printf '  harness： %s/       （控制台；未配模型时 AI 会报错）\n' "$HARNESS_BASE"
printf '\n'
printf '  体验：打开应用 → 右下角 AI 助手 → 问「列出条目并汇总库存」\n'
printf '  模型：首次使用先到 harness 控制台「设置 → 模型」添加渠道并点测试\n'
printf '  停止：按 data/runtime.json 的 pid 先 app 后 harness 执行 kill -TERM <pid>\n'
printf '        （Linux 没有 stop.ps1；stop.ps1 仅 Windows 用）\n'
printf '\n'
exit 0
