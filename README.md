# NoteMind — AI Agentic RAG 高级企业知识库

**架构方案：Java 主业务 + Python AI 引擎**  
参考：[LangChain + LangGraph Agentic RAG](https://www.bilibili.com/opus/1245196454660669448)

```text
Browser (web / admin)
        │  /api/**
        ▼
notemind-gateway :8080          ← Java
        │
        ▼
notemind-service :8081          ← Java（MySQL CRUD / 鉴权 / 编排）
        │  HTTP / SSE 转发
        ▼
backend (AI Engine) :8000       ← Python（LangChain / LangGraph / PGVector）
```

## 技术栈

| 层 | 技术 |
| --- | --- |
| 主业务后端 | JDK 17 · Maven · Spring Boot 3 · Spring Cloud Gateway · MyBatis-Plus · Spring Security · JWT |
| AI 引擎 | Python ≥ 3.11 · FastAPI · Tortoise/SQL 访问 · LangChain 1.x · LangGraph · sse-starlette |
| AI 能力 | 混合检索（向量+BM25+RRF）· Rerank · 查询改写 · 父子分块 · Agentic RAG · Function Calling · LLM-as-judge |
| 文档处理（AI 引擎） | PyMuPDF · pdfplumber · python-docx · openpyxl · RapidOCR · jieba |
| 数据 | MySQL 8（业务，Java 主写）· PostgreSQL + PGVector 1024 维（向量，AI 引擎主写）· Redis |
| 模型（百炼，管理端热更新） | `qwen-plus` · `text-embedding-v4` · `gte-rerank-v2` |
| 前端 | Vue 3 · Vite · Element Plus · Axios · ECharts · pnpm |

## 目录结构

```text
NoteMind/
├── platform/                 # Java 主业务（Gateway + Service + Client + Common）
│   ├── notemind-gateway/     # :8080
│   ├── notemind-service/     # :8081  DDD 四层
│   ├── notemind-client/      # Feign：含 AiEngineClient → Python
│   └── notemind-common/
├── backend/                  # Python AI 引擎 :8000（非全量业务后端）
│   └── app/{interfaces,application,domain,infrastructure,core}
├── frontend/                 # web :5173 · admin :5174
├── scripts/db/               # MySQL + PGVector
├── docs/
├── docker-compose.yml
└── README.md
```

### 职责怎么拆

| 能力 | 归属 |
| --- | --- |
| 登录注册、用户、网关鉴权 | `platform` |
| 系统配置（默认值热更新） | `platform`（MySQL `t_system_config`） |
| 知识库 / 切分策略 / 检索策略 / 模型 / Prompt / 工具登记 / 应用 / 会话 / 评测元数据 CRUD | `platform`（MySQL） |
| 解析、切分、向量化、混合检索、Agent 图、SSE 生成、Judge 打分 | `backend` |
| Agent Run/Step、对话消息、评测报告落库 | `platform` 写 MySQL（可由 AI 回调或 Java 编排写入） |
| PGVector / BM25 文本 | `backend` 写 PostgreSQL |

Java 调 AI：`infrastructure.ai.client` → `notemind-client.ai.AiEngineClient` → `http://127.0.0.1:8000`。

## 功能概览

### 管理员（前端 → Java → 必要时转 AI）

用户管理；**系统配置**（高频默认值：默认知识库/模型/策略、分页、上传扩展、Prompt 场景、TopK、温度、会话 LIMIT，Admin `/system/config`）；知识库与策略；文档上传后触发 AI 入库；片段修正；检索测试 / 召回调试台；模型与 Prompt；工具中心；Agent 执行与时间线；问答应用；对话日志；评测集 / 批量评测 / 对比看板。

### 用户

SSE 流式问答、引用溯源、多轮指代、会话管理、点赞点踩（经 Gateway → Service → AI Engine）。

### 可配置默认值

- 页面：`Admin` → **系统配置**（表 `t_system_config`，脚本 `scripts/db/21-system-config.sql`；中文标签修复见 `22-system-config-fix-labels.sql`）
- 回落：未入库时使用 `application.yml` 的 `notemind.defaults.*` 与 `.env.example` 中的环境变量
- 运行时：分页上限、检索 TopK、对话/Judge 温度等由 Java 读取并在调用 AI 引擎时下发；数值带硬上限防误配

## 数据库

| 库 | 脚本 | 主写方 |
| --- | --- | --- |
| MySQL `notemind` | `scripts/db/01-mysql-schema.sql` 及增量 `21`/`22`（系统配置）等 | Java |
| PG `notemind_vector` | `scripts/db/02-pgvector-schema.sql` | Python AI |

## 文档

| 文档 | 说明 |
| --- | --- |
| [开发计划](docs/plan/01-development-plan.md) | 分阶段任务、API Key、工具与验收 |
| [变更记录 · MVP](docs/changelog/01-mvp-stage0-4.md) | 阶段 0～4 已可测能力与验收路径 |
| [架构](docs/architecture/01-overview.md) | Java + Python 混合架构 |
| [API 规划](docs/api/01-endpoints.md) | 接口前缀 |
| [RAG 管线](docs/rag/01-pipeline.md) | 检索生成链路 |
| [本地部署](docs/deploy/01-local.md) | 启动步骤 |

## 快速启动

```powershell
# 0. 复制环境变量模板（勿提交真实密钥）
copy .env.example .env

# 1. 库（本机 MySQL/PG 已建好可跳过 compose；系统配置表执行 scripts/db/21-system-config.sql）
docker compose up -d

# 2. Java
cd platform
mvn -pl notemind-service,notemind-gateway -am package -DskipTests
# 启动 service :8081、gateway :8080

# 3. Python AI
cd backend
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# 4. 前端
cd frontend
pnpm install
pnpm dev:web
pnpm dev:admin
```

端口：Gateway `8080` · Service `8081` · AI `8000` · Web `5173` · Admin `5174`。  
浏览器只访问 Gateway；AI 引擎默认仅内网/本地。
