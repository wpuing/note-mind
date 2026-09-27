# 01 — NoteMind 混合架构总览

> 详细企业级设计见 [`02-enterprise-agentic-rag.md`](02-enterprise-agentic-rag.md)。

## 1. 运行时拓扑

```text
Admin :5174 / Web :5173
        │  /api/**
        ▼
notemind-gateway :8080          Java · JWT / CORS / 防伪造头
        │
        ▼
notemind-service :8081          Java · MySQL CRUD / 编排 / 落库
        │  AiEngineClient + AI_ENGINE_TOKEN
        ▼
AI Engine :8000                 Python · 解析切分向量检索 Agent SSE
        │
        ├── PostgreSQL + PGVector (子块向量)
        └── Redis 语义缓存；本地 JSON 图谱可测（Neo4j 规划）
```

| 组件 | 语言 | 职责 |
| --- | --- | --- |
| `platform` | Java | 业务、鉴权、CRUD、编排入口、运营数据、语义缓存/意图/图谱测试 |
| `backend` | Python | LangChain / LangGraph、检索与生成、向量写入 |
| `frontend` | Vue3 | 管理端 / 用户端 |

## 2. 四层概念映射（与原文方案对齐）

| 概念层 | NoteMind 承载 |
| --- | --- |
| 数据底座 | 文件解析切分、父子索引、MySQL 片段、PGVector；规划：Wiki 清洗强化、Neo4j |
| 检索引擎 | 混合检索（向量+BM25+RRF）、Rerank、父块回填、检索策略 |
| Agent 编排 | LangGraph Agentic RAG、工具、步骤回写 |
| 接入与网关 | Gateway + Service；语义缓存与意图规则路由可测；大小模型自动分流规划 |

## 3. 边界【强制】

见根目录开发约定文档 §1。浏览器只打 Gateway；AI 引擎不对公网暴露。

## 4. 演进优先级

1. **Phase A（已大体完成）**：有据问答 + 父子 + 重排 + 运营可测  
2. **Phase B（主体可测）**：语义缓存 + 意图路由测试页；大小模型按节点分流仍增强  
3. **Phase C（最小可测）**：本地 JSON 图谱；Neo4j + 多源并行仍规划  

详见 `02-enterprise-agentic-rag.md` §9。
