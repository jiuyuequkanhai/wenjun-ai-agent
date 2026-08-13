#!/bin/zsh

set -euo pipefail

PROJECT_DIR="${0:A:h}"
LOG_DIR="$PROJECT_DIR/tmp/logs"
PID_DIR="$PROJECT_DIR/tmp/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

load_keychain_secret() {
  local service_name="$1"
  local variable_name="$2"
  local secret_value
  secret_value="$(security find-generic-password -a "$USER" -s "$service_name" -w 2>/dev/null)" || {
    print -u2 "缺少钥匙串密钥：$service_name"
    exit 1
  }
  export "$variable_name=$secret_value"
  unset secret_value
}

load_keychain_secret "yu-ai-agent-deepseek" "DEEPSEEK_API_KEY"
load_keychain_secret "yu-ai-agent-dashscope" "DASHSCOPE_API_KEY"
load_keychain_secret "yu-ai-agent-amap" "AMAP_MAPS_API_KEY"
load_keychain_secret "yu-ai-agent-searchapi" "SEARCH_API_KEY"
load_keychain_secret "yu-ai-agent-pexels" "PEXELS_API_KEY"

if ! pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
  print "正在启动本地 PostgreSQL……"
  brew services start postgresql@18 >/dev/null
  for _ in {1..30}; do
    pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1 && break
    sleep 1
  done
fi

if ! pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
  print -u2 "PostgreSQL 启动超时，请先检查 brew services list。"
  exit 1
fi

if ! curl -fsS "http://127.0.0.1:11434/api/tags" >/dev/null 2>&1; then
  print "正在启动本地 Ollama……"
  brew services start ollama >/dev/null
  for _ in {1..30}; do
    curl -fsS "http://127.0.0.1:11434/api/tags" >/dev/null 2>&1 && break
    sleep 1
  done
fi

if ! ollama show qwen3-embedding:0.6b >/dev/null 2>&1; then
  print "正在下载本地 Qwen3 Embedding 模型……"
  ollama pull qwen3-embedding:0.6b
fi

if ! psql -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = 'yu_ai_agent'" | grep -q '^1$'; then
  print "正在创建本地知识库数据库……"
  createdb yu_ai_agent
fi
psql -d yu_ai_agent -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS vector" >/dev/null

if [[ -f "$PID_DIR/backend.pid" ]] || [[ -f "$PID_DIR/frontend.pid" ]]; then
  "$PROJECT_DIR/stop-local.command" >/dev/null 2>&1 || true
fi

print "正在构建图片搜索 MCP 服务……"
(
  cd "$PROJECT_DIR/yu-image-search-mcp-server"
  sh ./mvnw -q -DskipTests package
)

print "正在构建后端……"
(
  cd "$PROJECT_DIR"
  sh ./mvnw -q -DskipTests package
)

print "正在启动后端（DeepSeek + RAG + SearchAPI + MCP）……"
(
  cd "$PROJECT_DIR"
  nohup java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar >"$LOG_DIR/backend.log" 2>&1 &
  print $! >"$PID_DIR/backend.pid"
)

print "正在启动前端……"
(
  cd "$PROJECT_DIR/yu-ai-agent-frontend"
  if [[ ! -d node_modules ]]; then
    npm install
  fi
  nohup npm run dev -- --host 127.0.0.1 --port 3001 >"$LOG_DIR/frontend.log" 2>&1 &
  print $! >"$PID_DIR/frontend.pid"
)

backend_ready=0
for _ in {1..120}; do
  if curl -fsS "http://127.0.0.1:8123/api/health" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 1
done

if [[ "$backend_ready" != 1 ]]; then
  print -u2 "后端启动失败，最近日志如下："
  tail -n 80 "$LOG_DIR/backend.log" >&2
  exit 1
fi

frontend_ready=0
for _ in {1..30}; do
  if curl -fsS "http://127.0.0.1:3001/" >/dev/null 2>&1; then
    frontend_ready=1
    break
  fi
  sleep 1
done

if [[ "$frontend_ready" != 1 ]]; then
  print -u2 "前端启动失败，最近日志如下："
  tail -n 80 "$LOG_DIR/frontend.log" >&2
  exit 1
fi

print "启动完成："
print "  前端：http://127.0.0.1:3001/"
print "  后端：http://127.0.0.1:8123/api"
print "  日志：$LOG_DIR"
