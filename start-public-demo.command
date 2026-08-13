#!/bin/zsh

set -euo pipefail

PROJECT_DIR="${0:A:h}"
LOG_DIR="$PROJECT_DIR/tmp/logs"
PID_DIR="$PROJECT_DIR/tmp/pids"
TUNNEL_LOG="$LOG_DIR/cloudflared.log"
TUNNEL_PID_FILE="$PID_DIR/cloudflared.pid"
PUBLIC_URL_FILE="$PID_DIR/public-url.txt"

mkdir -p "$LOG_DIR" "$PID_DIR"

if ! command -v cloudflared >/dev/null 2>&1; then
  print -u2 "没有找到 cloudflared，请先执行：brew install cloudflared"
  exit 1
fi

export PUBLIC_DEMO_MODE=true
"$PROJECT_DIR/start-local.command"

print "正在创建临时公网网址……"
: >"$TUNNEL_LOG"
nohup cloudflared tunnel \
  --no-autoupdate \
  --url http://127.0.0.1:3001 \
  --logfile "$TUNNEL_LOG" \
  >>"$TUNNEL_LOG" 2>&1 &
print $! >"$TUNNEL_PID_FILE"

public_url=""
for _ in {1..60}; do
  public_url="$(grep -Eo 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | tail -n 1 || true)"
  [[ -n "$public_url" ]] && break
  if ! kill -0 "$(<"$TUNNEL_PID_FILE")" 2>/dev/null; then
    print -u2 "公网隧道启动失败，最近日志如下："
    tail -n 80 "$TUNNEL_LOG" >&2
    exit 1
  fi
  sleep 1
done

if [[ -z "$public_url" ]]; then
  print -u2 "等待公网网址超时，最近日志如下："
  tail -n 80 "$TUNNEL_LOG" >&2
  "$PROJECT_DIR/stop-local.command" >/dev/null 2>&1 || true
  exit 1
fi

print -r -- "$public_url" >"$PUBLIC_URL_FILE"
print ""
print "公网演示已启动："
print "  $public_url"
print ""
print "面试结束后双击 stop-local.command，公网网址会立即失效。"
