#!/usr/bin/env bash
# 远程一键部署：上传 jar → 生成远端重启脚本 → 启动 → 探活 → REST 安装/激活插件。
#
# 用法:
#   bash deploy_remote.sh -t user@host [选项]
#
# 选项:
#   -t user@host   目标服务器（必填，需已配置免密 SSH）
#   -d <dir>       远端部署目录，默认 /opt/jnf
#   -s <jar|skip>  底座 jar；skip=服务器上已有（默认 skip）
#   -a <jar>       业务应用 jar（可选）
#   -g <jar>       插件 jar（可选；上传后自动 install + activate）
#   -i <pluginId>  插件 id（-g 时必填）
#   -e <类全名>    插件入口主类（可选，缺省靠 jar 内 META-INF/services）
#   -N <显示名>    插件显示名（可选，默认=pluginId）
#   -v <版本>      插件版本（默认 1.0.0）
#   -P <port>      底座端口（默认 8090）
#   -A <port>      应用端口（默认 18081）
#   -h             显示本帮助
#
# 依赖: 本地 bash + ssh + scp；远端 Java 17+ + curl（systemd/防火墙不在本脚本范围内）。
# 说明: 远端服务用 nohup 后台启动，重启/停止看 $REMOTE_DIR/restart_all.sh 与 logs/*.log。
set -euo pipefail

TARGET=""; REMOTE_DIR="/opt/jnf"; HARNESS_JAR="skip"; APP_JAR=""; PLUGIN_JAR=""
PLUGIN_ID=""; ENTRYPOINT=""; PLUGIN_NAME=""; PLUGIN_VERSION="1.0.0"
DSH_PORT=8090; APP_PORT=18081

usage() {
  cat <<'USAGE'
远程一键部署：上传 jar → 生成远端重启脚本 → 启动 → 探活 → REST 安装/激活插件。

用法:
  bash deploy_remote.sh -t user@host [选项]

选项:
  -t user@host   目标服务器（必填，需已配置免密 SSH）
  -d <dir>       远端部署目录，默认 /opt/jnf
  -s <jar|skip>  底座 jar；skip=服务器上已有（默认 skip）
  -a <jar>       业务应用 jar（可选）
  -g <jar>       插件 jar（可选；上传后自动 install + activate）
  -i <pluginId>  插件 id（-g 时必填）
  -e <类全名>    插件入口主类（可选，缺省靠 jar 内 META-INF/services）
  -N <显示名>    插件显示名（可选，默认=pluginId）
  -v <版本>      插件版本（默认 1.0.0）
  -P <port>      底座端口（默认 8090）
  -A <port>      应用端口（默认 18081）
  -h             显示本帮助

依赖: 本地 bash + ssh + scp；远端 Java 17+ + curl（systemd/防火墙不在本脚本范围内）。
说明: 远端服务用 nohup 后台启动，重启/停止看 <远端目录>/restart_all.sh 与 logs/*.log。
USAGE
  exit "${1:-0}"
}

while getopts "t:d:s:a:g:i:e:N:v:P:A:h" opt; do
  case "$opt" in
    t) TARGET="$OPTARG" ;;
    d) REMOTE_DIR="$OPTARG" ;;
    s) HARNESS_JAR="$OPTARG" ;;
    a) APP_JAR="$OPTARG" ;;
    g) PLUGIN_JAR="$OPTARG" ;;
    i) PLUGIN_ID="$OPTARG" ;;
    e) ENTRYPOINT="$OPTARG" ;;
    N) PLUGIN_NAME="$OPTARG" ;;
    v) PLUGIN_VERSION="$OPTARG" ;;
    P) DSH_PORT="$OPTARG" ;;
    A) APP_PORT="$OPTARG" ;;
    h) usage 0 ;;
    *) echo "未知参数 -$opt（-h 看帮助）" >&2; exit 1 ;;
  esac
done

[ -n "$TARGET" ] || { echo "[FAIL] 缺少 -t user@host" >&2; usage 1; }
[ -n "$PLUGIN_JAR" ] && [ -z "$PLUGIN_ID" ] && { echo "[FAIL] 传了 -g 插件 jar 但没给 -i pluginId" >&2; exit 1; }
[ -z "$PLUGIN_NAME" ] && PLUGIN_NAME="$PLUGIN_ID"
HOST="${TARGET#*@}"

for f in "$APP_JAR" "$PLUGIN_JAR"; do
  [ -n "$f" ] && [ ! -f "$f" ] && { echo "[FAIL] 本地文件不存在: $f" >&2; exit 1; }
done
if [ "$HARNESS_JAR" != "skip" ] && [ ! -f "$HARNESS_JAR" ]; then
  echo "[FAIL] 底座 jar 不存在: $HARNESS_JAR（服务器已有就传 -s skip）" >&2
  exit 1
fi

echo "==> [1/6] 检查 SSH 连通性: $TARGET"
ssh -o ConnectTimeout=8 -o BatchMode=yes "$TARGET" "echo ok" >/dev/null 2>&1 || {
  echo "[FAIL] SSH 连不上 $TARGET" >&2
  echo "       检查：主机地址/端口、免密登录（ssh-copy-id）、安全组放行 22" >&2
  exit 1
}

echo "==> [2/6] 准备远端目录: $TARGET:$REMOTE_DIR"
ssh "$TARGET" "mkdir -p '$REMOTE_DIR/logs'"
if [ "$HARNESS_JAR" = "skip" ]; then
  ssh "$TARGET" "test -f '$REMOTE_DIR/jnf-harness-app.jar'" || {
    echo "[FAIL] 远端没有 $REMOTE_DIR/jnf-harness-app.jar" >&2
    echo "       要么先上传底座（-s <本地 jar 路径>），要么确认部署目录 -d 是否正确" >&2
    exit 1
  }
fi

echo "==> [3/6] 上传制品"
if [ "$HARNESS_JAR" != "skip" ]; then
  echo "    scp $(basename "$HARNESS_JAR") → $REMOTE_DIR/jnf-harness-app.jar"
  scp -q "$HARNESS_JAR" "$TARGET:$REMOTE_DIR/jnf-harness-app.jar"
else
  echo "    [SKIP] 底座 jar（假设服务器上已有 $REMOTE_DIR/jnf-harness-app.jar）"
fi
APP_NAME=""
if [ -n "$APP_JAR" ]; then
  APP_NAME="$(basename "$APP_JAR")"
  echo "    scp $APP_NAME → $REMOTE_DIR/$APP_NAME"
  scp -q "$APP_JAR" "$TARGET:$REMOTE_DIR/$APP_NAME"
fi
if [ -n "$PLUGIN_JAR" ]; then
  echo "    scp $(basename "$PLUGIN_JAR") → $REMOTE_DIR/$(basename "$PLUGIN_JAR")"
  scp -q "$PLUGIN_JAR" "$TARGET:$REMOTE_DIR/$(basename "$PLUGIN_JAR")"
fi

echo "==> [4/6] 生成远端重启脚本并启动"
RESTART_LOCAL="$(mktemp)"
{
  echo '#!/usr/bin/env bash'
  echo 'set -e'
  echo "cd '$REMOTE_DIR'"
  echo "pkill -f 'jnf-harness-app.jar' 2>/dev/null || true"
  if [ -n "$APP_NAME" ]; then
    echo "pkill -f '$APP_NAME' 2>/dev/null || true"
  fi
  echo 'sleep 1'
  echo "nohup java -jar '$REMOTE_DIR/jnf-harness-app.jar' --server.port=$DSH_PORT > '$REMOTE_DIR/logs/harness.log' 2>&1 &"
  if [ -n "$APP_NAME" ]; then
    echo "nohup java -jar '$REMOTE_DIR/$APP_NAME' --server.port=$APP_PORT > '$REMOTE_DIR/logs/app.log' 2>&1 &"
  fi
  echo 'sleep 3'
  echo "echo '远端进程:'; pgrep -af 'java -jar' || true"
} > "$RESTART_LOCAL"
scp -q "$RESTART_LOCAL" "$TARGET:$REMOTE_DIR/restart_all.sh"
rm -f "$RESTART_LOCAL"
ssh "$TARGET" "chmod +x '$REMOTE_DIR/restart_all.sh' && bash '$REMOTE_DIR/restart_all.sh'"

echo "==> [5/6] 探活（在远端本机 curl，避免公网端口未放行造成误判）"
probe_health() {
  ssh "$TARGET" "curl -s --max-time 5 'http://127.0.0.1:$DSH_PORT/actuator/health'" || true
}
HEALTH=""
for _ in $(seq 1 30); do
  HEALTH="$(probe_health)"
  case "$HEALTH" in *'"UP"'*) break ;; esac
  sleep 2
done
echo "    底座 /actuator/health → ${HEALTH:-（无响应）}"

APP_CODE="000"
if [ -n "$APP_NAME" ]; then
  APP_CODE="$(ssh "$TARGET" "curl -s -o /dev/null -w '%{http_code}' --max-time 5 'http://127.0.0.1:$APP_PORT/'" 2>/dev/null || true)"
  [ -n "$APP_CODE" ] || APP_CODE="000"
  echo "    应用 / → HTTP $APP_CODE"
fi

echo "==> [6/6] 安装并激活插件（REST，信封 code=00000 为成功）"
if [ -n "$PLUGIN_JAR" ]; then
  PAYLOAD_LOCAL="$(mktemp)"
  printf '{"pluginId":"%s","displayName":"%s","pluginVersion":"%s","runtimeType":"JAVA_NATIVE","sourcePath":"%s","entrypoint":"%s"}' \
    "$PLUGIN_ID" "$PLUGIN_NAME" "$PLUGIN_VERSION" "$REMOTE_DIR/$(basename "$PLUGIN_JAR")" "$ENTRYPOINT" > "$PAYLOAD_LOCAL"
  scp -q "$PAYLOAD_LOCAL" "$TARGET:$REMOTE_DIR/plugin_install.json"
  rm -f "$PAYLOAD_LOCAL"
  ssh "$TARGET" "curl -s --max-time 15 -X POST -H 'Content-Type: application/json' -d @'$REMOTE_DIR/plugin_install.json' 'http://127.0.0.1:$DSH_PORT/api/harness/plugins/install'" || true
  echo
  ssh "$TARGET" "curl -s --max-time 15 -X POST -H 'Content-Type: application/json' -d '{\"pluginId\":\"$PLUGIN_ID\"}' 'http://127.0.0.1:$DSH_PORT/api/harness/plugins/activate'" || true
  echo
else
  echo "    [SKIP] 未传 -g 插件 jar"
fi

echo ""
echo "===== 部署结果 ====="
case "$HEALTH" in
  *'"UP"'*) echo "[OK]   底座可访问: http://$HOST:$DSH_PORT（远端日志 $REMOTE_DIR/logs/harness.log）" ;;
  *)        echo "[FAIL] 底座未就绪：ssh $TARGET 'tail -80 $REMOTE_DIR/logs/harness.log'；确认 Java 17+ 与 $DSH_PORT 已放行" ;;
esac
if [ -n "$APP_NAME" ]; then
  if [ "$APP_CODE" = "200" ]; then echo "[OK]   应用可访问: http://$HOST:$APP_PORT"; else echo "[FAIL] 应用未就绪（HTTP $APP_CODE）：ssh $TARGET 'tail -80 $REMOTE_DIR/logs/app.log'"; fi
fi
if [ -n "$PLUGIN_JAR" ]; then
  STATUS_JSON="$(ssh "$TARGET" "curl -s --max-time 10 'http://127.0.0.1:$DSH_PORT/api/harness/plugins'" || true)"
  case "$STATUS_JSON" in
    *"\"pluginId\":\"$PLUGIN_ID\""*) echo "[OK]   插件已登记: $PLUGIN_ID（核对 status=ACTIVE: curl http://$HOST:$DSH_PORT/api/harness/plugins）" ;;
    *) echo "[FAIL] 插件未登记：看上面的 install 响应 info；本地可复用 node install_plugin.mjs 直连远程底座" ;;
  esac
fi
echo ""
echo "后续:"
echo "  1. 重启服务: ssh $TARGET 'bash $REMOTE_DIR/restart_all.sh'"
echo "  2. 看日志:   ssh $TARGET 'tail -50 $REMOTE_DIR/logs/harness.log'"
echo "  3. 模型渠道: 打开 http://$HOST:$DSH_PORT/ → 设置 → 模型，添加渠道并测试"
echo "  4. 交付验收: HARNESS=http://$HOST:$DSH_PORT；用 node smoke_test.mjs --base \$HARNESS --plugin $PLUGIN_ID 复检"
