# 文俊的超级助手

面向 FDE（Forward Deployed Engineer）作品集场景的 AI 应用平台，提供行业调研、个人知识库问答和通用超级智能体三类能力。

## 核心功能

| 功能 | 说明 |
| --- | --- |
| FDE 岗位调研助手 | 上传 PDF、DOC、DOCX 或粘贴资料，先输出主题、知识结构、案例和认知 Gap 规划，用户确认后再生成适合外行快速学习的完整材料 |
| 数字菜叔 | 读取本地 Markdown 文章，通过本地 Qwen3 Embedding 与 PGVector 构建 RAG 知识库，基于文章内容回答问题 |
| 文俊的超级助手 | 基于 ReAct 工作流自主规划并调用搜索、网页抓取、文件处理、PDF、地图和图片搜索等工具完成任务 |
| 对话历史 | 三个智能体均支持创建、恢复、重命名和删除历史对话，刷新页面后可以继续 |
| 文件解析 | 支持 PDF、DOC、DOCX 文件上传、文本提取和后续对话分析 |

## 技术栈

- Java 21、Spring Boot 3、Spring AI
- Vue 3、Vite
- DeepSeek 云端对话模型
- Ollama + `qwen3-embedding:0.6b` 本地向量模型
- PostgreSQL + PGVector
- SSE 流式响应、Tool Calling、MCP、ReAct Agent
- SearchAPI、Pexels、高德地图 MCP

## 本地运行

### 1. 环境要求

- JDK 21
- Node.js 18 或更高版本
- PostgreSQL，并安装 PGVector 扩展
- Ollama，并拉取 `qwen3-embedding:0.6b`

### 2. 配置

复制配置模板：

```bash
cp src/main/resources/application-local.example.yml \
  src/main/resources/application-local.yml
```

通过环境变量提供密钥，禁止把真实密钥提交到 Git：

```bash
export DEEPSEEK_API_KEY="你的 DeepSeek Key"
export DASHSCOPE_API_KEY="你的阿里云百炼 Key"
export AMAP_MAPS_API_KEY="你的高德 Key"
export SEARCH_API_KEY="你的 SearchAPI Key"
export PEXELS_API_KEY="你的 Pexels Key"
export CAISHU_SOURCE_DIRECTORY="你的 Markdown 文章目录"
```

根据本机 PostgreSQL 环境设置 `DB_USERNAME` 和 `DB_PASSWORD`。数据库初始化示例：

```sql
CREATE DATABASE yu_ai_agent;
\c yu_ai_agent
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

后端默认地址：`http://127.0.0.1:8123/api`

### 4. 启动前端

```bash
cd yu-ai-agent-frontend
npm install
npm run dev
```

前端默认地址：`http://127.0.0.1:3001`

macOS 本机也可以使用项目中的 `start-local.command` 和 `stop-local.command` 管理前后端进程。该脚本默认从 macOS 钥匙串读取密钥。

## 安全说明

- `application-local.yml`、构建产物和运行日志不会提交到仓库。
- 公共演示模式会禁用终端、任意文件访问和资源下载等高风险工具。
- 请定期轮换第三方 API Key，不要在 Issue、截图或提交记录中公开密钥。
