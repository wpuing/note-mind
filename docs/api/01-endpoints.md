# 01 — API 分组（规划）

## 浏览器 → Java Gateway

| 前缀 | 说明 |
| --- | --- |
| `/api/v1/auth/**` | 登录注册 |
| `/api/v1/ai-models` | 模型配置列表 |
| `/api/v1/strategies/**` | 切分 / 检索策略列表 |
| `/api/v1/knowledge/bases` | 知识库列表 / 管理 CRUD |
| `/api/v1/knowledge/documents/**` | 文档上传入库、列表、详情（Java 编排 AI） |
| `/api/v1/admin/**` | 管理端其它业务（规划） |
| `/api/v1/chat/**` | 用户会话与问答（Service 再调 AI SSE，规划） |

### 知识库 / 知识文档（已落地）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/knowledge/bases` | 启用中的知识库列表（上传下拉） |
| GET | `/api/v1/knowledge/bases/page` | 管理分页；可选 `name`、`status`、`page`、`pageSize`；含文档/片段数与向量汇总 |
| GET | `/api/v1/knowledge/bases/{id}` | 详情 |
| GET | `/api/v1/knowledge/bases/{id}/vector-documents` | 库内各文档向量汇总（供向量状态下钻） |
| POST | `/api/v1/knowledge/bases` | 新建（默认 `m_emb_v4` / `cs_recursive`） |
| PUT | `/api/v1/knowledge/bases/{id}` | 更新 |
| DELETE | `/api/v1/knowledge/bases/{id}` | 软删库及下属文档/片段，并尝试清 PGVector |
| POST | `/api/v1/knowledge/bases/batch-delete` | body `{ ids: [] }` 批量软删 |
| POST | `/api/v1/knowledge/documents/upload` | multipart：`file`、`knowledgeBaseId`、可选 `chunkSize`/`chunkOverlap` |
| GET | `/api/v1/knowledge/documents` | 分页列表；可选 `knowledgeBaseId`、`title`、`parseStatus`、`vectorIssue`、`page`、`pageSize`；返回含向量计数字段 |
| GET | `/api/v1/knowledge/documents/{id}/segments` | 文档片段分页；可选 `keyword`、`page`、`pageSize`（Java 转发 AI/PG） |
| GET | `/api/v1/knowledge/documents/{id}` | 详情（含 `knowledgeBaseName`） |

`parse_status`：`UPLOADED` → `PARSING` → `READY` \| `FAILED`。

## Java → Python AI Engine（内网）

| 前缀 | 说明 |
| --- | --- |
| GET | `/api/v1/ai/knowledge/documents/{document_id}/segments` | 按文档分页列片段（BM25 全文，可选 keyword） |
| GET | `/api/v1/ai/knowledge/bases/{knowledge_base_id}/vector-status` | 从 PG 汇总库/文档向量计数 |
| DELETE | `/api/v1/ai/knowledge/bases/{knowledge_base_id}/vectors` | 按知识库清理向量 / BM25 |
| `/api/v1/ai/ingest/documents/{document_id}` | 按文档 ID 入库（Java 主调） |
| `/api/v1/ai/retrieval/**` | 检索测试、召回调试 |
| `/api/v1/ai/agent/**` | Agentic RAG 执行 |
| `/api/v1/ai/chat/completions` | SSE 生成 |
| `/api/v1/ai/eval/**` | Judge 打分 |
| `/health` | 健康检查 |

Header：`X-AI-Engine-Token`。默认知识库 id：`kb_default`（`default` 会映射到此）。
