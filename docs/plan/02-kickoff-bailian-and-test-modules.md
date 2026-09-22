# 02 — 开工手册：百炼 Key · 测试模块补强 · 开工流程

> 基于 NoteMind 现状 + [`../architecture/02-enterprise-agentic-rag.md`](../architecture/02-enterprise-agentic-rag.md)。  
> 模型供应商**仅限阿里云百炼（DashScope）**。

---

## 一、还需要哪些模型 Key？（百炼）

### 1.1 先说结论

| 问题 | 答案 |
| --- | --- |
| 最少要几个 **API Key**？ | **1 个**百炼 `DASHSCOPE_API_KEY` 即可跑通当前 MVP（对话 + 向量 + 重排共用） |
| 管理端要配几类**模型**？ | **至少 3 类**：CHAT / EMBEDDING / RERANK（种子已有） |
| Phase B/C 还要新 Key 吗？ | **一般仍用同一 Key**；建议再登记 1～2 个**小对话模型**配置（同 Key，不同 `model_name`）做路由/抽取 |

> 百炼是「一个 Key 开通多模型」，不是每种模型各买一把 Key。  
> 若要做**配额隔离**（生产对话与离线抽图分开账单），可再申请第 2 个 Key，非必须。

### 1.2 当前 MVP 必备（现在就要）

在 [百炼控制台](https://bailian.console.aliyun.com/) 开通并拿到 Key，写入 `.env` 的 `DASHSCOPE_API_KEY`，并在 Admin「模型配置」为下列三条填入同一 Key（或分别测通）：

| 类型 | 种子 ID | 推荐模型名 | 用途 | 不配会怎样 |
| --- | --- | --- | --- | --- |
| CHAT | `m_chat_qwen` | `qwen-plus`（可升 `qwen-max`） | 问答生成、Agent 终答、评测 Judge、用例生成 | 问答/Agent/评测失败 |
| EMBEDDING | `m_emb_v4` | `text-embedding-v4`（**1024 维**） | 入库向量、检索、（规划）语义缓存 | 无法向量化/检索 |
| RERANK | `m_rerank_gte` | `gte-rerank-v2` | 检索重排（原生 API） | 完整策略效果变差；纯向量基线仍可跑 |

控制台侧请确认已开通上述模型的**推理权限**（未开通会 403/模型不存在）。

### 1.3 Phase B「降本」建议追加的模型配置（仍百炼、可共用 Key）

| 角色 | 建议模型名（百炼） | 用途 | 测试页是否要用 |
| --- | --- | --- | --- |
| 路由/改写/相关性小模型 | `qwen-turbo` 或 `qwen-plus` 的低成本档（按控制台实际可点名） | 意图 L1/L2/L3、Query 改写、grade | 意图路由测试、问答过程面板 |
| 终答大模型（可选升级） | `qwen-max` | 仅 L2 复杂生成 | 问答测试对比延迟/质量 |

**不需要**单独的「缓存模型 Key」：语义缓存复用 **EMBEDDING** 算相似度即可。

### 1.4 Phase C「图谱」建议（仍百炼）

| 角色 | 建议模型 | 用途 |
| --- | --- | --- |
| 离线实体关系抽取 | 小对话模型（同 Phase B 小模型） | Wiki → 三元组入 Neo4j |
| 在线图谱问答辅助 | 可用小模型做 Cypher/工具规划，终答仍走大模型 | Agent Tool |

图数据库本身**没有**百炼 Key；只有抽取/推理走百炼。

### 1.5 非模型但必配的密钥（易漏）

| 项 | 用途 |
| --- | --- |
| `JWT_SECRET`（≥32 字节） | 登录 |
| `AI_ENGINE_TOKEN` | Java → Python 内网调用 |
| MySQL / PostgreSQL / Redis 连接 | 业务库、向量库、缓存 |

---

## 二、补充架构后，测试相关页面要加哪些功能？

现有已可测：检索测试、召回调试台、问答测试、Agent 执行、评测三页、工作台 KPI。  
对照 Phase B/C，建议**增量**如下（按优先级）。

### 2.1 P0 — 增强现有「测试」页（先改这些，不必新开很多菜单）

| 页面 | 建议新增能力 | 对应架构 |
| --- | --- | --- |
| **问答测试** `/app` | ① 展示本次：意图级别 L1/L2/L3（或「沿用应用配置」）② 是否语义缓存命中（相似度）③ 各节点使用的模型名（小/大）④ Token/耗时拆分（检索/改写/生成） | 降本可观测 |
| **Agent 执行** `/ai/agent` | 时间线节点标注模型；反思次数；若启用图谱则显示「向量 / 图谱」两路命中 | Agentic + 多源 |
| **检索测试** `/knowledge/retrieval-test` | 开关：仅向量 / +BM25 / +Rerank / +父块回填；结果区分「子块命中 → 回填父块」预览 | 治本验收 |
| **召回调试台** `/knowledge/recall-bench` | 增加一列「预估成本档」（是否走重排/改写）；可选对比「缓存开启前后」 | 降本对比 |
| **工作台** `/dashboard` | 缓存命中率、L1/L2/L3 占比、平均 TTFT、估算 Token（有数据后再做） | 运营验收 |
| **模型配置** `/ai/model` | 标记用途标签：`GENERATOR` / `ROUTER` / `JUDGE` / `EMBED` / `RERANK`；连通性测试覆盖原生重排 | 大小模型协同 |

### 2.2 P1 — 建议新增的专用测试页（Phase B）

| 建议路由 | 名称 | 功能要点 |
| --- | --- | --- |
| `/ai/intent-test` | **意图路由测试** | 输入问题 → 输出 L1/L2/L3 + 置信度 + 将走直线还是 Agent；支持批量用例；可手工纠正并沉淀 |
| `/ai/cache-test` | **语义缓存测试** | 查询/清理（须 app 或 KB）；手工写入需 ADMIN + `NOTEMIND_ALLOW_MANUAL_CACHE_WRITE`；按用户隔离 |
| `/ai/model-route-test` | **大小模型路由试跑**（可与意图页合并） | 同一问题分别用「仅小模型」「小+大」跑改写/生成，对比耗时与字数 |

侧栏建议挂在现有「联调演示」或「AI」分组，占位页须写清依赖（Redis / 路由模型已配置）。

### 2.3 P2 — Phase C 测试能力

| 建议路由 | 名称 | 功能要点 |
| --- | --- | --- |
| `/knowledge/kg-test` | **图谱检索测试** | 选实体/自然语言 → 多跳结果；与向量命中并排 |
| 召回调试台扩展 | **多源并行对比** | 同 query：仅向量 vs 向量+图谱，阶段耗时与答案差异 |
| 评测集 | **复杂对比场景包** | 跨文档对比、多跳「谁负责哪部门」类用例模板 |

### 2.4 明确「现在不用做」的测试页

- 纯厂商 Playground 复刻（百炼控制台已有）  
- 未接 Neo4j 前不要做假图谱结果页  

---

## 三、开工流程（建议按日执行）

### Day 0 — 环境与 Key（半天）

1. 确认本机：MySQL `notemind`、PostgreSQL `notemind_vector`、Redis、JDK17、Python≥3.11、pnpm  
2. 百炼：创建/确认 **1 个 API Key**，开通 `qwen-plus`、`text-embedding-v4`、`gte-rerank-v2`  
3. 复制 `.env.example` → `.env`：填入 `DASHSCOPE_API_KEY`、`JWT_SECRET`、`AI_ENGINE_TOKEN`、库连接  
4. 启动：Gateway `:8080`、Service `:8081`、AI `:8000`、Admin `:5174`（可选 Web `:5173`）  
5. Admin 登录 → **模型配置** → 三条模型写入 Key → **测试连通**全部通过  

**出门标准**：三条模型连通成功；`GET :8000/health` 正常。

### Day 1 — 数据底座最小闭环（Phase A 验收）

1. 知识库：使用或新建 KB，绑定切分策略（建议先「递归通用」，再试「父子制度手册」）+ 检索策略（先基线，再完整）  
2. 文件管理：上传样例 → 切分 → 向量化  
3. 片段管理：确认子块「已向量」、父子策略下父块不入向量  
4. 检索测试：有命中；完整策略下能看到重排差异  
5. 问答测试（直线应用）：SSE 有字、`sources[]` 非空  

**出门标准**：上传 → 提问有据可查。

### Day 2 — Agentic 与评测基线

1. 应用管理：启用 Agentic 的应用「试一下」  
2. Agent 执行页：看时间线（改写/检索/判断/生成）  
3. 评测集：录入 10～20 条黄金问答（含易错切片题）  
4. 批量评测：基线策略 vs 完整策略跑一份报告  
5. 策略对比页看四维差异  

**出门标准**：有一份可复现的评测报告；Agent 路径可演示。

### Day 3 — 定 Phase B 开工项（降本，选做）

1. 在模型配置增加 **ROUTER 小模型**（同 Key，新 `model_name`）  
2. 排期开发顺序建议：  
   - ① 问答测试过程面板（意图/缓存/模型/耗时字段，可先 mock）  
   - ② 语义缓存（Redis + embedding 相似度）+ `/ai/cache-test`  
   - ③ 意图路由 + `/ai/intent-test`，L1 强制直线  
3. 每完成一项：更新 `docs/changelog` + `开发约定文档` 可测入口  

### Day 4+ — Phase C（图谱，后置）

1. 部署 Neo4j；实现 `infrastructure/kg`  
2. 离线抽取任务 + 工具登记  
3. `/knowledge/kg-test` 与召回多源对比  

---

## 四、推荐验收清单（打印用）

```text
[ ] 百炼 Key 已配且 CHAT/EMBED/RERANK 连通
[ ] Gateway/Service/AI/Admin 端口正常
[ ] 样例文档已切分并向量化
[ ] 检索测试有命中
[ ] 直线问答 SSE + sources
[ ] Agentic 时间线可看
[ ] 至少一份批量评测报告
[ ] （Phase B）缓存命中可演示 / 意图 L1 不进 Agent
[ ] （Phase C）图谱多跳可演示
```

---

## 六、最近一次联调结果（2026-09-20）

| 检查项 | 结果 |
| --- | --- |
| Day0/1 | 模型连通 + 上传检索问答有 sources |
| Day2 | Agent 5 步 SUCCESS；评测集 10 题；多份批量报告 COMPLETED |
| Day3 | 意图 L1/L2/L3 可测；语义缓存二次同问命中；问答 `meta` 面板 |
| Day4 | `/knowledge/kg-test` 实体/多跳可测（local-json） |

页面：`/ai/intent-test` · `/ai/cache-test` · `/knowledge/kg-test` · `/app`（过程面板）

---

## 五、文档索引

| 文档 | 用途 |
| --- | --- |
| [企业级说明书](../architecture/02-enterprise-agentic-rag.md) | 架构与差距 |
| [开发计划](01-development-plan.md) | 原分阶段任务 |
| 本文 | Key / 测试页增量 / 开工日程 |
| `开发约定文档` | 当前可测入口 |
