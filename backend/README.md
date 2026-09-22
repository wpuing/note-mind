# NoteMind Backend — Python AI 引擎

**不是**全量业务后端。Java（`platform/`）负责鉴权与 MySQL CRUD；本目录只做智能能力。

## 保留的域

| 包 | 用途 |
| --- | --- |
| `domain/knowledge` | ingest 侧片段/文档执行模型 |
| `domain/retrieval|agent|eval|chat` | 检索 / Agent / 评测 / SSE 问答 |
| `infrastructure/{parser,chunk,hybrid,rerank,graph,vector,...}` | 实际 AI 能力 |

已移除误仿 Java 的 `user/admin/model/prompt/app` CRUD 空包。

默认端口 **8000**。契约：`platform/notemind-client/.../AiEngineClient.java`。
