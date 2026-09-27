# platform — NoteMind Java 主业务后端

Maven 多模块：`notemind-common` / `notemind-client` / `notemind-gateway` / `notemind-service`。

## 职责边界（方案 2）

| 负责 | 不负责（交给 `backend/` Python AI 引擎） |
| --- | --- |
| 用户鉴权、JWT、网关 | LangChain / LangGraph 编排 |
| MySQL 业务 CRUD（知识库/策略/模型/Prompt/工具/应用/会话/评测元数据） | 文档解析、切分、Embedding、PGVector 写入 |
| 编排入口：调用 AI 引擎 | 混合检索 / Rerank / Agent 逐步推理 |
| 落库 Agent Run/Step、对话、评测报告 | SSE token 生成、LLM-as-judge 打分计算 |

基包 `com.notemind`。调用链：`*Ctl → *Asvc → *Dsvc → *Repository → *Mapper`。  
调 AI：`application → infrastructure.ai.client → notemind-client.ai.AiEngineClient → backend:8000`。
