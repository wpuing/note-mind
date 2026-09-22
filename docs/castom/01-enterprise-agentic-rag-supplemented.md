# 企业级 Agentic RAG 智能知识中枢 — 方案补充稿（基于 NoteMind）

> 本文是对 `🏢 项目名称：企业级 Agentic RAG 智能知识中枢.docx` 的**补充与落地改写**：保留原文「治本 / 降本 / 增效」与四层架构，绑定 NoteMind 现有双栈实现与可测入口。  
> **权威技术说明书**：[`../architecture/02-enterprise-agentic-rag.md`](../architecture/02-enterprise-agentic-rag.md)

---

## 项目名称

**企业级 Agentic RAG 智能知识中枢（NoteMind）**

解决企业知识库「数据质量差」与「大模型成本高、延迟大」两大痛点，实现高精度、低延迟、可控成本的智能问答与决策辅助。

## 核心设计理念

| 理念 | 含义 | NoteMind |
| --- | --- | --- |
| 治本 | 父子索引 + 知识图谱重塑数据底座 | 父子已落地；本地 JSON 图谱可测；Neo4j 规划 |
| 降本 | 意图路由、大小模型协同、缓存 | 语义缓存 + 意图规则路由可测；大小模型自动分流规划 |
| 增效 | LangGraph Agentic RAG | 改写/检索/反思/生成已落地 |

## 总体架构（四层 × 双栈）

原文四层不变，落地为：

1. **数据底座**：解析切分、父子索引、MySQL + PGVector（规划 Neo4j）  
2. **检索引擎**：向量 + BM25 + RRF + Rerank + 父块回填  
3. **Agent 编排**：LangGraph（Python AI Engine）  
4. **接入与网关**：Java Gateway/Service + Vue Admin/Web（语义缓存与意图规则路由可测）

```text
Browser → Gateway:8080 → Service:8081 → AI Engine:8000
                MySQL ←─────┘              └→ PGVector
```

## 技术选型（落地版）

| 模块 | 选定 |
| --- | --- |
| 主业务 | JDK17 · Spring Boot 3 · Gateway · JWT |
| AI 引擎 | FastAPI · LangChain · LangGraph · PGVector |
| 模型 | 百炼可配（对话/向量/重排） |
| 前端 | Vue3 Admin + Web |
| 缓存 | Redis；语义缓存可测（按用户隔离） |
| 图库 | 本地 JSON 可测；Neo4j 规划 |

## 实施路线图（与原文三阶段对齐）

| 阶段 | 目标 | NoteMind 状态 |
| --- | --- | --- |
| Phase A 基础 RAG 与数据治理 | 父子+重排+有据问答 | 主体 ✅，Wiki 清洗可再加强 |
| Phase B 路由与降本 | 语义缓存+意图分级+大小模型 | 缓存/意图测试页 ✅；大小模型分流规划 |
| Phase C Agentic 与图谱 | 图谱 Tool+并行检索 | LangGraph ✅；本地图谱可测；Neo4j ⬜ |

## 架构师忠告（沿用）

LangGraph 是骨架，大小模型协同是神经，高质量数据（父子 + 图谱）是血肉。优先数据治理与可观测评测，再增加 Agent 环路复杂度。

## 可测入口（摘要）

登录 → 知识库 → 文件管理（上传/切分/向量化）→ 检索测试 / 召回调试 → 问答（`/app` 或 Web `:5173`）→ 评测。详见 `开发约定文档`。
