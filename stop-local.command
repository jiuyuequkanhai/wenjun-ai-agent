#!/bin/zsh

set -u

PROJECT_DIR="${0:A:h}"
PID_DIR="$PROJECT_DIR/tmp/pids"

stop_from_pid_file() {
  local label="$1"
  local pid_file="$2"

  if [[ ! -f "$pid_file" ]]; then
    return
  fi

  local process_id
  process_id="$(<"$pid_file")"
  if [[ "$process_id" == <-> ]] && kill -0 "$process_id" 2>/dev/null; then
    pkill -TERM -P "$process_id" 2>/dev/null || true
    kill "$process_id" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$process_id" 2>/dev/null || break
      sleep 0.25
    done
    if kill -0 "$process_id" 2>/dev/null; then
      pkill -KILL -P "$process_id" 2>/dev/null || true
      kill -KILL "$process_id" 2>/dev/null || true
    fi
    print "已停止：$label"
  fi
  rm -f "$pid_file"
}

stop_from_pid_file "公网隧道" "$PID_DIR/cloudflared.pid"
stop_from_pid_file "前端" "$PID_DIR/frontend.pid"
stop_from_pid_file "后端" "$PID_DIR/backend.pid"
rm -f "$PID_DIR/public-url.txt"
