# 02 — 企业级 Agentic RAG 智能知识中枢 · 技术架构说明书

> **产品代号**：NoteMind  
> **文档定位**：以当前仓库已落地实现为基线，吸收《企业级 Agentic RAG 智能知识中枢》方案的四层设计、降本增效与图谱增强目标，给出**可对齐、可演进**的技术架构。  
> **读者**：架构 / 后端 / AI / 前端 / 实施负责人  
> **关联**：[`01-overview.md`](01-overview.md) · [`../rag/01-pipeline.md`](../rag/01-pipeline.md) · [`../plan/01-development-plan.md`](../plan/01-development-plan.md) · 根目录 `开发约定文档` / `README.md`

---

## 1. 文档与方案关系

| 来源 | 作用 |
| --- | --- |
| 原文方案（（内部方案材料，未随公仓分发）） | 理念：治本 / 降本 / 增效；四层架构；三阶段路线图 |
| NoteMind 现状 | Java 主业务 + Python AI 引擎；父子分块、混合检索、Rerank、LangGraph、SSE+sources、评测与运营后台已可测 |
| 本文 | **统一落地图**：原文概念 → NoteMind 模块映射；已实现 / 进行中 / 规划；技术选型取舍 |

### 1.1 核心设计理念（补充落地含义）

| 理念 | 原文要点 | NoteMind 落地 |
| --- | --- | --- |
| **治本** | 父子索引 + 知识图谱重塑 Wiki 底座 | ✅ 父子分块与父块回填已落地；⬜ 企业知识图谱（Neo4j 等）为 Phase C |
| **降本** | 意图路由、大小模型协同、缓存拦截无效请求 | 🟡 应用级「直线 / Agentic」可配；语义缓存 + L1/L2/L3 规则路由测试页已可测；大小模型自动分流仍为 Phase B 增强项 |
| **增效** | LangGraph Agentic RAG，并行检索与深度推理 | ✅ LangGraph（改写→检索→相关性判断→重试→生成）已落地；⬜ 多源并行边（向量+图谱）待图谱就绪后增强 |

### 1.2 两大痛点与对策

| 痛点 | 对策（原文） | NoteMind 对应能力 |
| --- | --- | --- |
| 数据质量差（垃圾进垃圾出） | Wiki 清洗 → Markdown；父子索引；图谱 | 多格式解析/OCR → 切分策略；仅子块入向量；片段人工修正；评测回流 |
| 成本高、延迟大 | 语义缓存；意图分级；大小模型协同；SSE | SSE 已通；检索策略七开关控成本；系统配置 TopK/温度；语义缓存/意图测试页可测；大小模型分流仍增强中 |

---

## 2. 总体架构：四层概念 × NoteMind 双栈

原文自下而上为：**数据底座 → 检索引擎 → Agent 编排 → 接入与网关**。  
NoteMind **不推翻四层**，而是用 **Java（鉴权/CRUD/编排入口）+ Python（AI 执行）+ Vue（双端）** 承载四层职责。

```text
┌─────────────────────────────────────────────────────────────────┐
│  L4 接入与体验层                                                  │
│  Admin :5174 · Web :5173 · 浏览器只打 Gateway                    │
└────────────────────────────┬────────────────────────────────────┘
                             │ /api/**
┌────────────────────────────▼────────────────────────────────────┐
│  L3 接入与网关层（Java）                                           │
│  notemind-gateway :8080 — 路由 / CORS / 剥离伪造用户头 / JWT       │
│  notemind-service :8081 — 业务编排、会话、落库、调 AI               │
│  【可测】语义缓存 · 意图路由 L1/L2/L3（规则）· 【规划】大小模型自动分流 │
└────────────────────────────┬────────────────────────────────────┘
                             │ AiEngineClient + AI_ENGINE_TOKEN
┌────────────────────────────▼────────────────────────────────────┐
│  L2 Agent 编排 + 检索引擎（Python AI Engine :8000）                │
│  LangGraph Agentic RAG · 混合检索(向量+BM25+RRF) · Rerank         │
│  直线 RAG / SSE 生成 · Judge · 工具(search_knowledge)              │
│  【规划】图谱 Tool · 多源并行边；【可测】本地 JSON 图谱（Java 测试页） │
└───────────────┬─────────────────────────────┬───────────────────┘
                │                             │
┌───────────────▼───────────┐   ┌─────────────▼───────────────────┐
│  L1 数据底座（双库）         │   │  模型与外部 AI（百炼等）           │
│  MySQL notemind（Java 主写） │   │  Chat / Embedding / Rerank       │
│  PG+PGVector（Python 主写）  │   │  Key 由 Java AES 落库，脱敏展示    │
│  解析·切分·父子·片段修正     │   └─────────────────────────────────┘
│  【规划】Wiki 清洗流水线强化 · Neo4j 知识图谱                        │
└───────────────────────────┘
```

### 2.1 强制边界（不可破坏）

| 做 | 放哪里 |
| --- | --- |
| 鉴权、用户、MySQL CRUD、网关、编排入口 | `platform/`（Java） |
| 解析 / 切分 / 向量化 / 检索 / Agent 图 / SSE / Judge | `backend/`（Python） |
| 调 AI | 仅经 `AiEngineClient`（或等价 HTTP） |
| Java 内写 LangChain/LangGraph/OCR | **禁止** |
| Python 做登录态与用户权限主逻辑 | **禁止** |

---

## 3. 分层详细设计

### 3.1 L1 数据底座层 — 解决「数据质量」

#### 3.1.1 文档治理流水线（现状 + 补强）

| 步骤 | 现状 | 原文要求 / 补强方向 |
| --- | --- | --- |
| 接入 | 文件管理上传存盘绑 KB | 增加 Wiki/HTML 批量导入与导航页脚剥离（清洗为 Markdown） |
| 解析 | PDF/DOCX/XLSX/MD/TXT + 限页 OCR 预览 | 统一「清洗后 Markdown」作为切分输入物 |
| 切分 | 递归 / 父子策略可配；种子含制度手册父子 | 子块建议 200–300 token 量级可按策略调；父块 1000+ 不入向量 |
| 向量化 | **仅子块**入 PGVector(1024)；父块供回填/BM25 | 与原文 Parent-Child 一致，保持 |
| 质检 | 片段管理可编辑删；向量状态可下钻 | 评测集 / 点踩沉淀形成数据飞轮 |

**父子索引机制（已实现，架构冻结）：**

```text
文档 → 父块(章节/长段, 不向量化) → 子块(短切片, Embedding)
检索命中子块 → 回填父块上下文 → 再交给 LLM 生成
```

#### 3.1.2 知识图谱（规划 · Phase C）

| 项 | 选型建议 | NoteMind 落点 |
| --- | --- | --- |
| 存储 | Neo4j / NebulaGraph | 新增基础设施，**不**替代 MySQL/PGVector |
| 抽取 | 小模型离线抽取实体关系 | Python `infrastructure/kg/`（规划包） |
| 消费 | Agent Tool（如 `search_knowledge_graph`） | 工具中心登记 + LangGraph Tool 节点 |
| 边界 | 图谱查询在 Python；元数据/权限仍在 Java | 浏览器永不直连图库 |

#### 3.1.3 存储职责

| 存储 | 库/组件 | 主写 | 内容 |
| --- | --- | --- | --- |
| 业务库 | MySQL `notemind` | Java | 用户、KB、文档元数据、片段文本、策略、模型配置、会话、评测、Agent 步骤等 |
| 向量库 | PostgreSQL + PGVector `notemind_vector` | Python | 子块向量（MVP 选型；高并发可迁 Milvus/Qdrant，接口隔离） |
| 缓存 | Redis | Java | 会话辅助；**语义缓存**（精确/余弦，按用户隔离，问答链路落库） |
| 对象/文件 | 本地 uploads（可换 OSS） | Java | 原始文件 |

> **选型说明**：原文推荐 Milvus/Qdrant。NoteMind MVP 用 **PGVector** 降低运维复杂度；检索引擎通过 `infrastructure/vector` 抽象，迁移时不改业务域接口。

---

### 3.2 L2 检索引擎层 — 多路召回与重排

| 通路 | 原文 | NoteMind |
| --- | --- | --- |
| 向量检索 | Milvus/Qdrant | ✅ PGVector 余弦检索 |
| BM25 | 专有名词兜底 | ✅ 混合检索中的关键词通路 |
| 融合 | RRF | ✅ RRF **只看名次** |
| Rerank | Cross-Encoder / bge / Cohere | ✅ DashScope 原生重排 → LangChain Compressor 回退 |
| 阈值 | — | ✅ 余弦 / 重排双阈值 + 检索策略七开关 |
| 图谱检索 | Neo4j 多跳 | 🟡 本地 JSON 图谱测试页可测；Neo4j 仍规划 |
| 父块回填 | 命中子块返父块 | ✅ `enable_parent_child` 等策略开关 |

**检索策略运营**：管理端「检索策略 / 检索测试 / 召回调试台」支持基线纯向量、进阶混合、完整链路对比，用于验收「治本」与开关效果。

---

### 3.3 L2/L3 Agent 编排层 — LangGraph Agentic RAG

#### 3.3.1 状态机（现状对齐原文）

| 原文节点 | NoteMind 现节点（概念） |
| --- | --- |
| Query_Analysis | 查询改写 / rewrite |
| Tool_Router | 应用配置直线 vs Agentic；工具 `search_knowledge` |
| Parallel_Retrieval | 当前为混合检索单管线；图谱并行为规划 |
| Self_Reflection | grade 相关性 + 有限轮次重试（防死循环） |
| Generation | 大模型生成 + SSE；必须 `sources[]` |

#### 3.3.2 反思与成本护栏

- 最大重试轮次可配（默认小值，避免死循环烧 Token）
- Judge / 相关性评估建议 **temperature=0**
- Context Recall 评测用 `source_segment_ids`
- Agent 每步可回传 Java 写入 `t_agent_step`，管理端时间线可观测

---

### 3.4 L3/L4 接入与网关层 — 成本与延迟

| 能力 | 原文 | NoteMind |
| --- | --- | --- |
| API 网关 | 流量清洗与分发 | ✅ Spring Cloud Gateway：路由、CORS、防伪造头 |
| 鉴权 | — | ✅ JWT；管理写接口 ADMIN；Web 会话归属校验 |
| 语义缓存 | Redis + 向量相似度 >0.95 直接返答 | ✅ Service 编排前命中可跳过 AI；`/ai/cache-test`；手工写入默认关 |
| 意图路由 | L1 直线 / L2 Agent / L3 拒答 | 🟡 规则分类 `IntentRouteAsvc` + `/ai/intent-test`；应用级开关并存；小模型分类仍增强 |
| 大小模型协同 | 小模型路由/改写/评估，大模型终答 | 🟡 模型配置多套；⬜ 按节点自动选模 |
| 流式 | SSE | ✅ 经 Gateway 转发的聊天 SSE |

**意图分级（目标行为）：**

| 级别 | 典型问题 | 行为 |
| --- | --- | --- |
| L1 简单事实 | 「WiFi 密码」 | 直线 RAG，禁止进 Agent 循环 |
| L2 复杂推理 | 跨文档对比 + 流程指引 | LangGraph Agentic RAG |
| L3 闲聊/越权 | 无关或敏感 | 拒答 / 兜底话术 / 转人工（应用配置） |

---

## 4. 逻辑部署与端口

| 进程 | 端口 | 职责 |
| --- | --- | --- |
| `notemind-gateway` | 8080 | 统一入口 |
| `notemind-service` | 8081 | DDD 业务 |
| AI Engine (`backend`) | 8000 | 解析检索 Agent SSE（内网） |
| Admin | 5174 | 运营与评测 |
| Web | 5173 | 用户问答 |
| MySQL | 3306 | 业务 |
| PostgreSQL | 5432 | 向量 |
| Redis | 6379 | 缓存 + 语义缓存 |

浏览器 **禁止**直连 `:8000` 与携带 AI Engine Token。

---

## 5. 核心业务旅程（补充落地路径）

**示例问题**：「对比去年 X1 与今年 X2 在续航和散热上的区别，并告诉我该向哪个部门申请测试机？」

| 步骤 | 目标架构行为 | 当前可测做法 |
| --- | --- | --- |
| 1 网关 | JWT 校验 → 语义缓存查询 | 登录后进 Web/问答；缓存命中可走 `pipeline=cache` |
| 2 意图 | 小模型判 L2 → 激活 Agentic + 大模型生成 | 规则意图 + 应用 Agentic 开关；`/ai/intent-test` |
| 3 规划检索 | 拆子任务；向量+父子召回手册；图谱查「测试机申请」部门 | 向量+父子+混合检索；`/knowledge/kg-test` 本地图谱 |
| 4 重排反思 | Rerank；不足则改写二次检索 | ✅ grade + rewrite 轮次 |
| 5 流式生成 | SSE 输出对比与指引 + sources | ✅ `/app` 或 Web 前台 |

---

## 6. 技术选型对照表

| 模块 | 原文建议 | NoteMind 选定 | 说明 |
| --- | --- | --- | --- |
| 编排 | LangGraph | ✅ LangGraph（Python） | 骨架 |
| 基础组件 | LangChain / LlamaIndex | ✅ LangChain 系 | 在 AI 引擎内 |
| 主业务 | （未强调） | ✅ Java Spring Boot 3 + Gateway | 企业鉴权/CRUD/运营 |
| 生成模型 | Qwen-Max / GPT-4o 等 | 管理端可配（如 qwen-plus） | 可升 Max |
| 小模型 | Qwen2.5-7B / mini | 可登记小对话模型；按节点自动选模仍规划 | Phase B 增强 |
| 向量库 | Milvus / Qdrant / ES | ✅ PGVector 1024 | 可迁移 |
| 图数据库 | Neo4j / Nebula | 🟡 本地 JSON 可测；Neo4j 规划 | Phase C |
| 缓存 | Redis + 向量插件 | ✅ Redis 语义缓存（Java） | Phase B 主体可测 |
| 重排 | bge-reranker / Cohere | ✅ gte-rerank-v2（百炼） | 可换 |
| 前端 | — | Vue3 Admin + Web | 运营+问答 |

---

## 7. 代码与包结构（架构视图）

```text
NoteMind/
├── platform/                      # L3 网关与业务编排（Java）
│   ├── notemind-gateway/
│   ├── notemind-service/          # DDD: interfaces → application → domain → infrastructure
│   ├── notemind-client/           # AiEngineClient 契约
│   └── notemind-common/
├── backend/app/                   # L1 执行部分 + L2 检索/Agent（Python）
│   ├── interfaces/                # /api/v1/ai/**
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│       ├── parser · chunk         # 数据底座
│       ├── vector/pgvector · hybrid · rerank
│       ├── graph/                 # LangGraph Agentic RAG
│       ├── tools/
│       ├── cache/                 # 【扩展】语义缓存
│       └── kg/                    # 【规划】知识图谱客户端
├── frontend/admin · web           # L4
├── scripts/db/
└── docs/architecture/             # 本文档所在
```

业务域（Java）：`user` · `knowledge` · `model` · `prompt` · `retrieval` · `agent` · `app` · `chat` · `eval` · `system`。

---

## 8. 安全架构

1. 浏览器 → 仅 Gateway；AI 引擎内网  
2. `AI_ENGINE_TOKEN`：Java→Python  
3. JWT ≥32 字节；管理写 ADMIN；Web 会话归属  
4. 模型 Key AES-GCM 落库，前端脱敏  
5. 禁止提交真实密钥；公开导出走脱敏流程  

---

## 9. 能力成熟度与路线图

对齐原文 Phase 1/2/3，并标注 NoteMind **已超前完成的运营面**（后台 CRUD、评测、召回调试等）。

### Phase A — 基础 RAG 与数据治理（原文 Phase 1）

| 项 | 状态 |
| --- | --- |
| 上传 → 切分 → 向量 → 检索 → SSE+sources | ✅ |
| 父子索引 | ✅ |
| Rerank + 混合检索 | ✅ |
| 片段修正 / 策略运营 / 评测 | ✅（原文未强调，本项目增强） |
| Wiki/HTML 深度清洗流水线 | 🟡 部分解析；待强化 |

**验收参考**：有据可查；切片不再导致明显上下文丢失；评测召回/精度可量化。

### Phase B — 路由分发与成本优化（原文 Phase 2）

| 项 | 状态 |
| --- | --- |
| SSE 全链路 | ✅ |
| 语义缓存（Redis + 相似度阈值） | ✅（按用户隔离；手工写默认关） |
| 意图路由 L1/L2/L3 自动化 | 🟡 规则可测；小模型分类仍增强 |
| 大小模型按节点分流 | ⬜ |
| Token/延迟看板 | 🟡 工作台部分 KPI |

**验收参考**：平均延迟下降、Token 成本下降（目标量级对齐原文 40% / 50%，以压测基线为准）。

### Phase C — Agentic 演进与图谱增强（原文 Phase 3）

| 项 | 状态 |
| --- | --- |
| LangGraph Agentic + 反思重试 | ✅ |
| 企业知识图谱 + Tool | 🟡 本地 JSON 图谱测试页；Neo4j Tool 仍规划 |
| 多数据源并行检索边 | ⬜ |
| 复杂跨文档对比场景专项评测集 | 🟡 能力具备，场景可持续建设 |

---

## 10. 架构师原则（采纳并本地化）

> **LangGraph 是骨架，大小模型协同是神经，高质量文档（父子索引 + 未来图谱）是血肉。**

1. **先数据后图**：优先清洗与父子策略、评测闭环，再堆 Agent 环路复杂度。  
2. **能直线不 Agent**：L1 问题禁止进循环。  
3. **可观测**：步骤时间线、召回调试台、四维评测先于「感觉调参」。  
4. **边界清晰**：Java 管权与账，Python 管智与向量。  
5. **选型可替换**：向量库 / 重排模型 / Chat 模型经配置与接口替换，不绑死厂商。

---

## 11. 文档维护

公仓仅同步架构 / API / RAG / 部署说明；内部开工手册、变更记录与方案补充稿不随仓库分发。模型密钥仅写入本地 `.env`（见 `.env.example`）。


---

## 12. 附录：相对原文方案的落地补充

在 NoteMind 语境下，对原文的**实质性补充**：

1. **双栈企业落地**：增加 Java Gateway/Service、JWT、运营后台、评测与系统配置，而非纯 Python 演示栈。  
2. **向量选型务实化**：MVP 用 PGVector；保留迁 Milvus 的架构缝。  
3. **可测运营面**：知识库/文件/片段/策略/模型/Prompt/工具/应用/对话日志/评测对比，保证「架构可验收」。  
4. **安全默认**：浏览器不直连 AI；Key 不落前端。  
5. **路线图与现状对齐**：明确 ✅/🟡/⬜，避免把规划写成已完成。  
6. **用户旅程绑定现有入口**：Admin `/app`、Web `:5173`、召回调试台、批量评测。
