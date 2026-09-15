# 01 — Agentic RAG 管线（Python AI 引擎执行）

由 Java `platform` 触发，在 `backend` 执行：

1. 上传元数据落 MySQL（Java）→ 调用 `/api/v1/ai/ingest/**`  
2. 解析 / OCR / 切分 — `infrastructure/parser|chunk`  
3. 子块 Embedding → PGVector(1024)  
4. 混合检索：向量 + BM25 → RRF — `infrastructure/hybrid`  
5. Rerank → 阈值 — `infrastructure/rerank`  
6. 父块回填 → LangGraph — `infrastructure/graph` → SSE  

评测 Judge 在 Python 算分，报告主数据由 Java 写入 `t_eval_report`。
