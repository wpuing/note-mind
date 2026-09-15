# NoteMind 开发计划

> 架构：Java `platform`（业务）+ Python `backend`（AI 引擎）+ Vue `frontend`  
> 目标能力对齐：[Agentic RAG 专栏](https://www.bilibili.com/opus/1245196454660669448)  
> 当前状态：库表已建；管理端壳子已通；业务/AI 实现多为脚手架

---

## 一、开工前必须具备

### 1. 账号与密钥（必填）

| 项 | 用途 | 获取方式 | 写入位置 |
| --- | --- | --- | --- |
| **阿里云百炼 `DASHSCOPE_API_KEY`** | 对话 / Embedding / 重排 | [百炼控制台](https://bailian.console.aliyun.com/) → API-KEY | 根目录 `.env`；也可后续在管理端「模型配置」加密存库 |
| （可选）独立 Chat Key | 若对话与向量分账号 | 同上 | `t_ai_model_config` |
| （可选）独立 Embedding / Rerank Key | 同上 | 同上 | 同上 |

默认模型（可在管理端改，热更新）：

| 类型 | 模型名 | 接口形态 |
| --- | --- | --- |
| 对话 | `qwen-plus` | OpenAI 兼容 |
| 向量 | `text-embedding-v4`（**1024 维**） | OpenAI 兼容 |
| 重排 | `gte-rerank-v2` | DashScope **原生**（非 OpenAI 兼容） |

本地还需自备（已有则可跳过）：

| 项 | 说明 |
| --- | --- |
| MySQL 8 | 库 `notemind`，脚本 `scripts/db/01-mysql-schema.sql` |
| PostgreSQL + PGVector | 库 `notemind_vector`，脚本 `scripts/db/02-pgvector-schema.sql` |
| Redis | 验证码 / 会话缓存 |
| `JWT_SECRET`（≥32 字节） | `.env` |
| `AI_ENGINE_TOKEN` | Java ↔ Python 内网调用校验 |

### 2. 本机工具链

| 工具 | 版本建议 | 用途 |
| --- | --- | --- |
| JDK | ≥ 17 | `platform` |
| Maven | ≥ 3.8 | 构建 Java |
| Python | ≥ 3.11 | `backend` |
| Node.js | ≥ 18 | 前端 |
| pnpm | 9.x（与 workspace 一致） | 前端依赖 |
| Git | — | 版本管理 |
| Docker Desktop（可选） | — | `docker compose` 起依赖 |
| IDE | AI 助手 / IDEA / VS Code | 开发 |

### 3. 推荐辅助工具

| 工具 | 用途 |
| --- | --- |
| **pgAdmin / DBeaver / Navicat** | 看 MySQL + PGVector |
| **Vector Inspector**（可选） | 向量库可视化（`pip install "vector-inspector[pgvector]"`） |
| **Postman / Apifox / Bruno** | 调 Gateway / AI Engine 接口 |
| **Knife4j** | Java Service 文档（`:8081/doc.html`，实现后） |
| **Chrome** | 管理端 SSE / 流式联调 |

### 4. 端口一览

| 服务 | 端口 |
| --- | --- |
| Gateway | 8080 |
| Java Service | 8081 |
| Python AI Engine | 8000 |
| Admin 前端 | 5174 |
| Web 用户端 | 5173 |
| MySQL | 3306 |
| PostgreSQL | 5432 |
| Redis | 6379 |

---

## 二、当前进度（基线）

| 模块 | 状态 |
| --- | --- |
| MySQL / PGVector 建库建表 | ✅ |
| Java DDD 骨架 + Gateway/Service | ✅ 壳；鉴权/CRUD 未实装 |
| Python AI 引擎骨架 | ✅ 壳；`/health` 级 |
| Admin 布局（侧栏+顶栏+路由） | ✅ UI 壳；页面多为占位 |
| 用户端 Web | ⏳ 未做 |
| Java ↔ AI Feign/HTTP | ⏳ 契约占位 |
| 端到端 RAG 问答 | ⏳ |

---

## 三、分阶段计划

### 阶段 0 — 环境打通（0.5～1 天）

**目标**：四进程可启动，管理端能登录拿 Token。

| 任务 | 负责 |
| --- | --- |
| 填好 `.env`（含 `DASHSCOPE_API_KEY`） | 你 |
| 确认 MySQL / PG / Redis 可连 | 你 |
| Java：`Result`/`ErrorCode`/`BaseEntity`、JWT 登录、验证码 | platform |
| Gateway：路由 + CORS + 剥伪造用户头 | platform |
| Admin：登录对接真实 `/api/v1/auth/**` | frontend/admin |
| Python：启动 + Token 中间件校验 `AI_ENGINE_TOKEN` | backend |

**验收**：浏览器登录管理端 → Gateway → Service 返回 Token；`GET :8000/health` 通。

---

### 阶段 1 — 配置中枢（2～3 天）

**目标**：模型 / Prompt / 切分策略 / 检索策略可在管理端维护，改完立刻生效。

| 任务 | 负责 |
| --- | --- |
| 用户 CRUD（管理员） | platform + admin |
| `t_ai_model_config` CRUD + 连通性测试（调百炼） | platform → 可轻调 AI |
| `t_prompt_template` CRUD | platform + admin |
| `t_chunk_strategy` / `t_retrieval_strategy` CRUD | platform + admin |
| AI 引擎只读拉配置（或任务参数下发） | backend |

**需要**：百炼 Key；管理端模型页、策略页从占位改为表单。

**验收**：三种模型配置可保存；点「测试连通」成功；策略七开关可编辑。

---

### 阶段 2 — 知识入库链路（4～6 天）【核心】

**目标**：上传文档 → 解析 → 切分 → 向量化 → PGVector 可查。

| 任务 | 负责 |
| --- | --- |
| 知识库 / 文档元数据 CRUD、上传落盘 | platform |
| 触发 ingest 任务、文档状态机 | platform |
| PDF/Word/Excel/MD/TXT 解析 + OCR | backend |
| 递归分块 / 父子分块 | backend |
| Embedding 写入 PGVector + BM25 文本 | backend |
| 片段列表、人工修正、重新向量化 | platform + admin + backend |

**需要**：

- 百炼 Embedding Key  
- 若干测试文档（含扫描件 PDF 测 OCR）  
- 本机磁盘上传目录写权限  

**验收**：一篇 PDF 状态到 `READY`；PG 中有 1024 维向量；管理端能改片段并重嵌入。

---

### 阶段 3 — 检索与调试台（3～5 天）【核心】

**目标**：混合检索可跑，量纲与阈值正确，调试台可对比。

| 任务 | 负责 |
| --- | --- |
| 向量检索 + BM25（jieba）+ RRF | backend |
| 查询改写（多查询 / 指代 / HyDE） | backend |
| DashScope Rerank 封装为 LangChain Compressor | backend |
| 父块回填 | backend |
| 检索测试 API（分阶段耗时、分数、量纲） | backend |
| 召回调试台（最多 4 套策略并排） | admin + platform 编排 |

**需要**：百炼 Chat + Embedding + Rerank；已入库知识。

**验收**：专有名词题（如型号）混合检索优于纯向量；调试台能并排看召回。

---

### 阶段 4 — 问答应用与 SSE（3～4 天）

**目标**：用户/管理端都能基于应用做流式问答 + 引用溯源。

| 任务 | 负责 |
| --- | --- |
| `t_qa_app` 绑定知识库/策略/Prompt | platform + admin |
| 会话 / 消息落库、点赞点踩 | platform |
| SSE 生成 + sources[] | backend |
| Gateway/Service 转发 SSE（注意缓冲） | platform |
| 管理端问答测试页；用户端聊天页（可并行） | frontend |

**需要**：百炼 Chat；阶段 2～3 完成。

**验收**：SSE 逐字出字；答案下有可展开引用；点踩写入原因。

---

### 阶段 5 — Agentic RAG（4～6 天）

**目标**：LangGraph 自主多轮检索/改写/反思；过程可复盘。

| 任务 | 负责 |
| --- | --- |
| 状态图：retrieve → evaluate → rewrite → generate → reflect | backend |
| 循环上限、步骤回传 | backend |
| `t_agent_run` / `t_agent_step` 落库 | platform（接收回调或同步写） |
| 工具中心登记 + 真开关 + 调用日志 | platform + backend |
| 管理端 Agent 执行 / 时间线 | admin（可接 ECharts/时间轴） |

**需要**：百炼 Chat；工具若调外部 API 另备对应 Key。

**验收**：同一问题两次路径可不同；时间线能回放节点耗时与输入输出。

---

### 阶段 6 — 评测闭环（3～5 天）

**目标**：LLM-as-judge 四指标 + 策略对比看板。

| 任务 | 负责 |
| --- | --- |
| 评测集：手工 / 文档生成 / 点踩沉淀 | platform + backend |
| 用例带 `source_segment_ids`（Context Recall 集合运算） | backend |
| 批量跑评测报告四指标（Judge T=0） | backend |
| 对比看板柱状图 | admin + ECharts |

**需要**：百炼 Chat（裁判模型建议固定、temperature=0）。

**验收**：基线 vs 完整策略分数可对比；报告可落库复看。

---

### 阶段 7 — 用户端与打磨（2～4 天）

| 任务 | 负责 |
| --- | --- |
| 用户注册登录、头像、会话管理 | platform + web |
| 多轮指代消解体验 | backend + web |
| 权限、限流、审计日志、上传病毒扫描（按需） | platform |
| 性能：批量嵌入、异步队列（Redis/线程池） | platform + backend |

---

## 四、推荐实施顺序（最短闭环）

```text
阶段0 环境与登录
  → 阶段1 模型/策略配置
  → 阶段2 文档入库
  → 阶段3 检索调试
  → 阶段4 SSE 问答   ← 第一个可演示的「企业知识库」
  → 阶段5 Agent
  → 阶段6 评测
  → 阶段7 用户端打磨
```

**第一个可对外演示的里程碑**：阶段 4 结束（上传资料 → 提问有据可查）。

---

## 五、依赖与风险清单

| 风险 | 应对 |
| --- | --- |
| 百炼额度 / 限流 | 开发用小文档；评测集控制条数 |
| Rerank 非 OpenAI 兼容 | 必须自写 Compressor，勿假设 OpenAI SDK 直通 |
| 向量维度必须 1024 | 换模型要改表维度并重建索引 |
| SSE 经 Gateway 被缓冲 | 关缓冲 / 直接 Service 调试对照 |
| OCR 依赖体积大 | RapidOCR 可选装；无扫描件可先关 |
| Security 默认拦截 | 阶段 0 必须落地正式 JWT，替换临时 permitAll |

---

## 六、你现在就可以准备的清单（Checklist）

- [ ] 开通阿里云百炼，创建 **API Key**，写入 `.env` 的 `DASHSCOPE_API_KEY`
- [ ] 确认本机 MySQL / PostgreSQL(PGVector) / Redis 账号密码与 `.env` 一致
- [ ] 安装 JDK17、Maven、Python3.11+、Node18+、pnpm
- [ ] 准备 3～5 份企业内部样例文档（含 1 份扫描 PDF 更佳）
- [ ] （可选）安装 DBeaver / Navicat、Apifox
- [ ] 克隆/打开本仓库后：`platform` / `backend` / `frontend` 按 README 能分别启动

准备好 Key 与样例文档后，建议从 **阶段 0 → 阶段 2** 连续推进，尽快打通入库与问答。
