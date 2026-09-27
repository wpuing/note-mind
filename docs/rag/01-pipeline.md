# 01 — Agentic RAG 管线

由 Java `platform` 触发，在 `backend` 执行。完整架构见 [`../architecture/02-enterprise-agentic-rag.md`](../architecture/02-enterprise-agentic-rag.md)。

## 主链路（已落地）

```text
上传元数据落 MySQL（Java）
  → 解析 / OCR（Python）
  → 切分（递归 / 父子；仅子块待向量化）
  → Embedding → PGVector(1024)
  → 混合检索：向量 + BM25 → RRF（只看名次）
  → Rerank → 双阈值
  → 父块回填
  → 直线生成 或 LangGraph（改写→检索→grade→重试→生成）
  → SSE + sources[]
```

## 与企业方案用户旅程对齐

| 步骤 | 目标 | 现状 |
| --- | --- | --- |
| 语义缓存 | 相似度命中直接返 | ✅ Java Redis 语义缓存；`/ai/cache-test` |
| 意图路由 L1/L2/L3 | 简单走直线，复杂走 Agent | 🟡 规则路由可测；应用配置并存 |
| 并行多源检索 | 向量 + 图谱 | 🟡 本地图谱可测；Neo4j / 并行边规划 |
| 自我反思 | 不足则改写重检 | ✅ 有限轮次 |
| 流式 + 引用 | SSE + sources | ✅ |

## 评测

Judge 在 Python 算分（建议 `temperature=0`）；报告主数据由 Java 写入 `t_eval_report`。Context Recall 使用 `source_segment_ids`。
