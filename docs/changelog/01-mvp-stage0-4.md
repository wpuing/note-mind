# 变更记录 · 阶段 0～4 MVP 可测

> 约定：功能补充 / 新增 / 修改 / 优化后，在本文件追加条目，并同步 `项目内部约定/` 与 `开发约定文档` 中的「当前可测能力」。

## 2026-09-14（Web 前端 + Java 后端密集注释）

- `frontend/web`：入口/路由/鉴权 API/布局/登录/个人中心/问答首页补充中文注释（含分支与 SSE）
- `platform`：interfaces / application / domain / infrastructure 以及 client、gateway、common 方法 Javadoc + 注入调用/控制流行内注释；修复注释过程中破坏的分隔符字面量并编译通过

## 2026-09-14（抽取脚手架）

- 新增 `scripts/export_scaffold.py`：脱敏抽取可复用骨架到 `D:/data/scaffold/NoteMind`（排除密钥、logs/bat、语料、构建产物）

## 2026-09-14（README 同步系统配置）

- README 补充系统配置模块、脚本 `21`/`22`、默认值回落与安全启动说明（不含本地密钥/演示口令）

## 2026-09-14（系统配置加固：种子/校验/接线）

- 种子与建表只跑一次（`AtomicBoolean`），避免每次打开配置页刷库
- 缓存整体替换；保存时同步快照，减少 reload 空窗
- int/decimal 硬上限（分页≤500、会话≤500、消息≤2000、TopK≤200、温度 0～2）；拒绝 NaN/Infinity
- 乱码修复收紧为：种子含非 ASCII + 现值无非 ASCII + 含 `??`
- TopK/温度接到检索参数、`AiEngineHttpClient`、Agent/评测生成；Agent 下发 `judge_temperature`；Python `clamp_top_k` 软截断

## 2026-09-14（系统配置 prompt.scenarios 值乱码）

- `prompt.scenarios` 的 `config_value` 仍为 `????,...`（上次只修了 label）；已用 `22` SQL 回写中文场景枚举
- 启动种子：若现值含 `?` 且种子含非 ASCII，一并修复 `config_value`（不误伤用户正常英文/数字配置）

## 2026-09-14（JDBC 连接失败修复）

- 根因：`characterEncoding=utf8mb4` 非 Java 字符集名 → `Unsupported character encoding 'utf8mb4'` / Failed to obtain JDBC Connection
- 修复：改回 `characterEncoding=UTF-8`，保留 `connectionCollation=utf8mb4_unicode_ci`

## 2026-09-14（系统配置中文乱码修复）

- 根因：种子/导入未用 utf8mb4，库内 `label`/`description` 落成字面量 `?`；已有行被 `seedIfAbsent` 跳过
- 修复：`22-system-config-fix-labels.sql` 回写展示文案；JDBC 用 `UTF-8` + utf8mb4 collation；启动种子改为 upsert 元数据（不覆盖用户 `config_value`）

## 2026-09-14（系统配置模块 · 页面可改）

- 新增 `t_system_config`（`scripts/db/21-system-config.sql`）；Service 启动亦可自动建表并种子
- Java：`SystemConfigAsvc` 内存缓存 + CRUD；高频默认值（默认 KB/模型/策略、分页、上传扩展、Prompt 场景、TopK、温度、会话 LIMIT）业务侧优先读库
- Admin：`/system/config` 分组编辑保存；侧栏「系统配置」
- 未改库时仍回落 `notemind.defaults.*` / `.env`

## 2026-09-14（默认值可配置化）

- Java 新增 `NoteMindDefaultsProperties`（`notemind.defaults.*`）：缺省 KB/向量模型/切分/检索策略、分页上限、Prompt 场景、上传扩展名
- 知识库 / Prompt / 文档 / 检索 / Agent / AiEngineClient 改为读取配置，去掉散落的 `kb_default` / `m_emb_v4` 等硬编码
- Python 新增 `app/core/defaults.py`；Settings 增加 top_k / temperature / CORS；路由与 LangGraph 统一 `resolve_knowledge_base_id`

## 2026-09-13（全流程 API 冒烟 + 公仓脱敏发布）

- 新增 `scripts/e2e_full_flow.py`：覆盖 Admin/Web/AI（登录、DDD CRUD、配置列表、上传切分向量化、检索、SSE、Agent）
- 本地体检：Java 编译通过；MySQL 需可用（本次曾以 mysqld 直启恢复）
- 公仓：`scripts/export_public_repo.py` 脱敏导出后推送 https://github.com/wpuing/note-mind

## 2026-09-13（DDD 样板：KnowledgeBase / PromptTemplate）

- 打通样板调用链：`*Ctl → *Asvc → *Dsvc → *Repository`（Jdbc 实现，暂不引入 MyBatis Mapper）
- 知识库：`KnowledgeBase` entity + `KnowledgeBaseDsvc` + `KnowledgeBaseRepository` / `JdbcKnowledgeBaseRepository`；Asvc 仅保留 AI 向量 enrich / 清向量编排
- Prompt：`PromptTemplate` entity + `PromptTemplateDsvc` + `JdbcPromptTemplateRepository`；Asvc 只做 VO 转换与事务边界
- 其它业务域仍为 Asvc+Jdbc，后续按同模式迁移

## 2026-09-13（审计续修：LangGraph / AiEngineClient / Compressor）

- Agent 执行改走 Python LangGraph（`POST /api/v1/ai/agent/run`）；Java 只落库步骤与工具日志
- Judge / 直线生成 / 评测 LLM 一律经 `AiEngineClient` → `POST /api/v1/ai/llm/chat`（不再直连 DashScope）
- 重排：优先 LangChain `DocumentCompressor`，失败回退 DashScope 原生
- 前端：文档下拉超限提示；Dashboard 空 catch 打日志；会话列表上限 100

## 2026-09-13（审计续修收口：半修复 + P1/P2）

- **补完半修复**：SSE 在检索/生成关键节点检查 `cancelled`；评测开跑 `synchronized` 互斥 RUNNING；知识库列表不再逐行打 AI；文档 `vectorIssue` SQL 分页；去掉列表 PG/AI fallback；chat/retrieval `default`→`kb_default`；401 同步清 Pinia
- **P1**：会话 ID 全量 UUID；向量化前清同文档向量；`prod/staging` 默认密钥启动失败；Gateway 要求 Bearer；Admin 去掉 `/api/v1/ai/` 直连代理；Agent Judge `temperature=0` LLM
- **P2**：消息列表 LIMIT 500；ILIKE 转义；jieba/OCR 线程锁；问答切应用复用会话；Web 顶栏固定；登录页不再预填密码

## 2026-09-13（审计续修：密钥加密 / 连接池 / 并发）

- 模型 `api_key_enc`：AES-GCM 落库（`enc:v1:`），历史明文可读兼容；列表脱敏前先解密
- Python：`psycopg_pool` 连接池；检索/解析/embedding 走 `asyncio.to_thread`；SSE 异常仍发 `final{sources}`+`done`
- 文档向量化 CAS（`parse_status<>EMBEDDING`）；批量评测单任务互斥 + 删除即取消停写

## 2026-09-13（审计修复：安全 / 检索正确性 / 性能）

- **P0**：会话/消息强制 `user_id` 归属；赞踩需会话归属；SSE 断开可取消工作线程
- **本周**：切分/入库 `extract_text(preview=False)`；多查询 RRF 不再误用余弦阈值；双路未开 RRF 时合并 BM25；重排候选截断；Web `local-*` 死循环；前后端 SSE `AbortController`
- **随后**：Agent/召回调试去掉长事务；文档列表片段状态一次聚合；父块写入 BM25 供回填；移除前端直连 AI Token/API
- **中期（部分）**：去掉明文密码兼容；管理写接口需 `ROLE_ADMIN`；空 `AI_ENGINE_TOKEN` 拒绝调用；token 恒定时间比较

## 2026-09-13（管理端顶栏固定）

- Admin 全局顶栏（面包屑 + 用户）在页面内容出现滚动条时保持固定，不随内容上移；仅 `.admin-content` 滚动

## 2026-09-13（个人资料 + 修改密码）

- Admin `/profile`：展示账号信息；编辑昵称/头像地址；修改密码（成功后强制重新登录）
- 登录改为校验 `t_user`（BCrypt）；启动/登录时自动种子 `admin`/`demo`（密码取自 `DEMO_*` 环境变量）
- Java：`GET /api/v1/auth/me`、`PUT /api/v1/auth/profile`、`PUT /api/v1/auth/password`；仅 `/login` 放行，其余 auth 需 JWT
- 顶栏显示当前用户昵称；退出清除本地用户缓存

## 2026-09-13（工作台：热门知识库与使用图表）

- Admin `/dashboard`：近 7/14/30 天 KPI + 图表（使用趋势、热门知识库、高频提问、模型调用柱状图、类型占比）
- Java：`GET /api/v1/dashboard/overview?days=`，聚合会话提问、Agent、工具调用、召回调试与应用绑定模型
- 口径：向量/重排调用按 `search_knowledge` 检索次数关联到当前启用的向量/重排配置

## 2026-09-13（同类问题排查：工具实现标记 + 直线问答落日志）

- 扫描结论：其它运行类模块（Agent 运行/步骤、对话日志、评测报告、召回调试）均有真实写入；无第二份「纯演示日志」种子
- 工具中心：`list_documents` / `get_document_detail` 此前标「已实现」但未接入执行 → 改为未实现（`19-agent-tool-implemented-align.sql`）；OpenAI 定义仅导出已启用且已实现
- 直线问答检索同样写入 `search_knowledge` 调用日志（无 agent_run_id）；Agentic 仍走 Agent 链路带运行 ID

## 2026-09-13（工具调用日志：接真实写入）

- 根因：`/ai/tool-log` 此前仅展示 `11-agent-tool-call-log-demo.sql` 假数据；Agent 检索从未写入 `t_agent_tool_call_log`
- 修复：`AgentRunAsvc` 每次 `search_knowledge`（知识库检索）成功/失败均落真实日志，含 `agent_run_id` / 入参 / hits / 耗时
- 清理：`18-clear-tool-call-log-demo.sql`；废弃 `11` 演示导入脚本
- 页面文案标明：无数据时需先跑 Agent 执行或 Agentic 问答

## 2026-09-13（列表页批量删除补齐）

- 扫描 Admin 菜单列表页，为缺批量删除的页面补齐多选 + `POST .../batch-delete`
- AI 配置：模型 `/ai/model`、Prompt `/ai/prompt`、工具 `/ai/tool`、工具日志 `/ai/tool-log`、Agent 运行 `/ai/agent-run`
- 其它：应用管理 `/app/manage`、评测集用例 `/eval/dataset`、召回调试历史 `/knowledge/recall-bench`
- 通用请求体 `IdsBatchDeleteRequest`；工具调用日志为硬删（表无 soft-delete 字段）
- 已有批量删除未改：知识库 / 文件 / 片段 / 切分策略 / 检索策略 / 批量评测报告

## 2026-09-13（召回调试台）

- Admin `/knowledge/recall-bench`：选知识库 + 最多 4 套策略 + 可选限定文档 →「开始对比」
- 结果弹窗按策略 Tab 查看阶段耗时表与命中片段；历史调试记录可回看/删除
- Java：`POST /api/v1/knowledge/recall-bench/compare`、分页/详情/删除；表 `t_recall_debug_run`（`17-recall-bench.sql`）

## 2026-09-13（演示语料重置 + 多格式入库）

- 新建演示知识库与大切分策略种子
- 多格式样例文档请本地自行准备（不随仓库分发）
- 管理端「文件管理」上传后切分 / 向量化即可验收


## 2026-09-12（批量评测：批量删除）

- Admin `/eval/batch`：任务列表多选 + 顶栏「批量删除」；软删 `t_eval_report`
- Java：`POST /api/v1/eval/reports/batch-delete`，body `{ ids }`，返回 `{ deleted }`

## 2026-09-12（修复：混合≈基线同分解）

- 根因：BM25 对中文整句 `plainto_tsquery`/`ILIKE 整句` 几乎不命中 → 混合策略 BM25=0，退化为纯向量，与基线结果一致
- 修复：`keyword_search` 用 jieba 分词后 OR 检索 + 按词 ILIKE 打分；入库/重建 `content_tsv` 前先分词空格化
- 脚本：`scripts/refresh_bm25_tsv.py` 重建已有 BM25 词表；`scripts/diag_eval_strategy_diff.py` 对比策略召回
- 建议：清理评测集脏用例（乱码问句、无标准答案的点踩沉淀）后重新跑批量评测再对比

## 2026-09-12（用户前台问答）

- Web `:5173`：登录（演示账号（见 `.env.example`））→ 首页选问答应用 → 新建对话 / 我的对话 → SSE 流式问答
- 回答展示过程元信息、引用来源折叠、赞踩反馈；顶栏首页 / 个人中心
- Java：`GET /api/v1/chat/sessions?appId=` 按当前用户列会话；创建会话支持 `clientSource=WEB`，绑定 JWT userId

## 2026-09-12（策略对比看板）

- Admin `/eval/compare`：按评测集筛选已完成报告；勾选 ≥2 份 →「对比选中的报告」
- 分组柱状图（ECharts）：上下文召回 / 精度 / 忠实度 / 答案相关性；下方矩阵表
- 列表展示策略启用能力标签（向量/BM25/重排/多查询扩展/父块回填）与单条平均耗时
- Java：`GET /api/v1/eval/reports/page` 支持 `datasetId` / `status`，JOIN 策略开关

## 2026-09-12（批量评测）

- Admin `/eval/batch`：选评测集 + 检索策略 → 开始评测；任务列表（状态/进度/耗时/看报告/删除/批量删除）；RUNNING 自动轮询
- 评测报告：四维卡片（上下文召回 / 上下文精度 / 忠实度 / 答案相关性）+ 达标阈值 + 用例明细表
- 执行：检索（策略）→ RAG 生成答案 → Judge（temperature=0）；Context Recall 优先 `source_segment_ids` 集合运算
- Java：`/api/v1/eval/reports/**`；迁移 `16-eval-batch.sql`（任务进度字段 + Judge Prompt）

## 2026-09-12（评测集管理）

- Admin `/eval/dataset`：评测集下拉 + 新建/删除；用例列表（来源/参与评测/看原文/编辑/删除）
- 手工录入、从文档自动生成（对话模型读片段出问答对）、从点踩沉淀（待沉淀 DISLIKE→用例，需补标准答案）
- 「看原文」核对问题/标准答案与来源片段快照
- Java：`/api/v1/eval/**`；迁移 `15-eval-dataset.sql`
- 修复：JWT 失效时不再整页「渲染失败」；清 Token 并回 `/login`（`assertBizOk` + `App.vue` onErrorCaptured）

## 2026-09-11（对话日志与反馈）

- Admin `/chat-log`：顶部点赞/点踩/满意度；Tab「对话日志」「用户反馈」
- 对话日志：按标题搜索；列含会话ID/应用/标题/消息数/来源/赞踩/时间；「查看」弹窗对话详情（气泡+引用来源）
- 用户反馈：按类型/是否沉淀筛选；列含类型/问题/回答/原因/已沉淀/时间；点击沉淀标签切换
- Java：`/api/v1/chat-logs`（stats / sessions / feedback）；迁移 `14-chat-log.sql`（`client_source` / `settled`）

## 2026-09-11（问答测试：聊天 UI + Agentic 过程状态）

- Admin `/app` 改为会话式问答：选应用、新建会话、会话 ID、气泡对话、回车发送
- 过程状态：正在检索 / 判断召回是否相关 / 已检索到资料正在生成；引用来源可展开；赞踩反馈
- Agentic 应用：展示步数/检索/改写 +「查看时间线」；未命中可改写后最多 3 轮检索
- Java：`POST /api/v1/chat/stream`（SSE）+ 会话/消息/反馈；直线链路与 Agentic 统一入口

## 2026-09-11（应用管理：挂载知识库 + 检索策略）

- Admin `/app/manage`：应用名称/状态筛选；列表含挂载知识库、Agentic/直线链路、历史条数、状态；新增/编辑/删除；「试一下」跳转 `/app?appId=&knowledgeBaseId=`
- 编辑弹窗：名称、说明、挂载知识库、检索策略、Agentic 模式、历史消息条数、兜底话术、状态
- Java：`/api/v1/qa-apps` 分页 CRUD + 启用列表；迁移 `scripts/db/13-qa-app.sql`（`history_limit`/`fallback_reply` + 三套演示应用）
- 侧栏「问答应用」增加「应用管理」；问答测试可回显当前应用

## 2026-09-11（Agent 执行 + 运行记录）

- Admin `/ai/agent`：选知识库 + 问题 →「开始执行」；展示结论标签、步数/检索/改写/耗时、回答与引用来源；「查看执行时间线」
- Admin `/ai/agent-run`：按状态筛选分页；列含运行ID/类型/状态/步数/检索/改写/结论/失败原因/耗时/时间；行内「时间线」弹窗
- 侧栏「AI 配置」与工具中心同级：Agent 执行、Agent 运行记录
- Java：`/api/v1/agent-runs`（`page` / `{id}` / `execute`）；MVP 链路 rewrite→retrieve→grade→generate→check，写入 `t_agent_run` / `t_agent_step`
- 迁移：`scripts/db/12-agent-run.sql`（补充 error/rewrite/kb/run_type/sources/conclusion 字段）

## 2026-09-11（调用统计按勾选过滤 + 弹窗可拖拽）

- 工具中心勾选后，「调用统计」仅返回所选工具；按钮文案同步提示已选数量
- 公共 `AppModal` 默认支持标题栏拖拽移动（`draggable`，可关）

## 2026-09-11（工具调用日志 + 与统计关联）

- Admin `/ai/tool-log`：按工具/结果/运行ID筛选；列含日志ID、运行ID、工具、结果、入参、失败原因、耗时、时间；详情弹窗
- 工具中心「调用统计」可点次数/成功/失败/「日志」跳转到调用日志并带 `toolCode`（及可选 status）筛选
- Java：`/api/v1/agent-tool-call-logs`；演示数据 `scripts/db/11-agent-tool-call-log-demo.sql`

## 2026-09-11（工具定义标明当前条目）

- 列表可勾选；顶部按钮在有勾选时只导出已选工具，文案变为「查看已选工具定义（N）」
- 行内增加「定义」；弹窗顶部展示「当前查看：code（名称）」及工具标签，避免分不清是哪一条

## 2026-09-11（工具中心对齐外部系统）

- Admin `/ai/tool`：名称/启用筛选；列表含编码、描述、已实现、启用开关、排序；新增/编辑/删除
- 「查看模型收到的工具定义」：已启用工具转为 OpenAI `tools[]` JSON 预览
- 「调用统计」：按 `t_agent_tool_call_log` 聚合（暂无调用则为 0）
- Java：`/api/v1/agent-tools`；迁移 `scripts/db/10-agent-tool.sql`（`sort_no`/`implemented` + 三套种子）

## 2026-09-11（Prompt 模板配置对齐外部系统）

- Admin `/ai/prompt`：按名称/场景筛选分页；列含编码、名称、场景、变量、内容摘要、启用；操作预览/编辑/删除
- 编辑弹窗：编码、名称、场景、内容（`{var}` 占位）、启用、备注；保存时自动解析变量写入 `variables_json`
- 预览弹窗：按变量动态填值 →「生成预览」渲染回填结果
- Java：`/api/v1/prompt-templates` 分页 CRUD；迁移 `scripts/db/09-prompt-template.sql`（`scenario` + 六套种子）

## 2026-09-11（模型配置下拉修复 + 市面模型目录）

- AppModal 内 `el-select` 增加 `teleported` + `app-modal-select-popper`（z-index 高于遮罩），下拉可点
- 服务商 / 接口地址 / 模型名称改为可筛选下拉，内置百炼/OpenAI/DeepSeek 等常见选项，支持自定义输入；选模型可联动协议与地址

## 2026-09-11（模型配置编辑：地址虚化 / 密钥提示）

- 重排原生协议补真实 `base_url`（不再空值只显示灰色 placeholder）
- 编辑弹窗展示库中已保存密钥的脱敏提示（不明文回填，避免掩码误写回库）；三套百炼密钥继续从根目录 `.env` 写入库

## 2026-09-11（模型配置 404 + 三套百炼种子）

- 根因：Admin Vite 代理 `/api/v1/ai` 前缀误匹配 `/api/v1/ai-models`，请求打到 Python `:8000` 返回 404；改为 `/api/v1/ai/`（尾斜杠）
- MySQL 补齐/刷新三套：`百炼对话/向量/重排模型配置`（`m_chat_qwen` / `m_emb_v4` / `m_rerank_gte`）

## 2026-09-11（AI 模型配置完整 CRUD）

- 列表：按名称/类型筛选、分页；类型彩色标签；密钥脱敏；操作含测试/编辑/删除
- 编辑弹窗：配置名称、类型、协议、服务商、地址、API Key、模型名、维度、启用、备注
- Java：`AiModelConfigAsvc` 分页 CRUD + 连通性测试（向量/对话/重排）

## 2026-09-11（检索测试知识库片段数不准）

- `/api/v1/knowledge/bases` 列表补上 `documentCount` / `segmentCount` 统计，下拉不再显示「片段 0 条」

## 2026-09-11（检索测试结果区对齐三套策略）

- 开始检索后展示：策略摘要 / 阶段耗时表 / 改写问题标签 / 命中明细表（名次、召回来源、分数量纲、文档、类型、子块、内容）
- Python 返回 `stages`、`elapsed_ms`、`score_scale_label`、`recall_source`；基线/进阶/完整阶段差异可区分

## 2026-09-11（检索测试对齐外部系统）

- 检索测试表单：知识库 / 限定文档（可多选）/ 检索策略（可选覆盖）/ 问题 →「开始检索」
- Java 支持 `retrievalStrategyId`、`documentIds`；Python 向量/BM25 按文档过滤
- 与知识库、文件管理、检索策略互跳（支持 `?knowledgeBaseId=&documentId=&retrievalStrategyId=`）

## 2026-09-11（弹窗底部按钮避让任务栏）

- AppModal 用 `svh`/`dvh` 限制高度，底部加安全间距，表单脚栏始终可见，不被系统任务栏挡住

## 2026-09-11（弹窗/面板取消虚化）

- `--surface` 改为实心白；AppModal / 步骤块 / 遮罩去掉半透明与 `backdrop-filter`，详情内容不再透底发糊
- 顶栏、登录卡同步去掉毛玻璃

## 2026-09-11（知识库列表：去掉横向滚动）

- 压缩知识库表格列宽；取消操作列 `fixed="right"`（去掉右侧竖线/滚动条）；长文本悬停看全文

## 2026-09-11（文件管理：上传时间不被裁切）

- 压缩文件列表列宽；操作列用小圆角按钮；大小列过长省略，悬停显示完整值（含字节）
- 取消 `fixed`，避免遮挡；时间完整可见

## 2026-09-11（检索策略完整落地）

- 检索策略 CRUD + 外部系统同款七开关编辑器；种子三套：基线纯向量 / 进阶混合 / 完整混合+重排+改写
- 知识库绑定 `retrieval_strategy_id`；检索测试走 Java `/api/v1/knowledge/retrieval/test` 按 KB 策略下发
- Python `hybrid_retrieve` 遵守七开关：多查询/指代消解/HyDE、余弦/重排阈值、父块回填；RRF 名次分不做阈值砍
- 迁移：`scripts/db/08-retrieval-strategy.sql`（或 `scripts/apply_08_retrieval.py`）

## 2026-09-10（片段管理：操作列 + 手动向量化状态）

- 片段管理操作列加宽，编辑/删除不再被裁切；内容悬停可看全文
- 切分只写入「待向量化」片段，不自动向量化；向量化仅处理 PENDING/FAILED，失败标 FAILED
- 片段列表增加「向量化状态」筛选（待向量化/已向量化/失败/跳过）

## 2026-09-10（文件列表大小/时间不换行）

- 文件管理「大小」「上传时间」列加宽 + `nowrap` / 不换行空格，避免 `4901.0 KB`、日期时间被挤成两行

## 2026-09-10（大文件解析超时）

- Gateway `response-timeout: 600s`；Service AI `read-timeout-ms: 300000`、`parse-timeout-ms: 600000`
- 解析预览 `preview=true`：OCR 限前 5 页、降 DPI、够字即停；避免扫描大 PDF 拖垮请求
- 前端解析提示与超时文案优化

## 2026-09-10（PDF 解析空文本 + 弹窗去虚化）

- PDF 解析增强：PyMuPDF → pdfplumber → RapidOCR（扫描件）；解析落盘用短文件名避免超长中文路径问题
- 已安装 `rapidocr-onnxruntime`；空结果返回 `hint` 提示
- 全部 AppModal / Element Plus overlay：强制去掉 `backdrop-filter` 毛玻璃虚化

## 2026-09-10（文件批量删除不完整）

- 根因：批量删时逐条清向量易超时，请求中断后只删掉前一部分
- 修复：先批量软删本地再尽力清向量；前端全选跨页时询问是否删「全部筛选结果」；失败则逐条兜底

## 2026-09-10（文件管理 / 切分策略批量删除）

- 文件管理、切分策略列表增加勾选 +「批量删除」；接口 `POST .../documents/batch-delete`、`POST .../chunk-strategies/batch-delete`
- 切分策略：默认策略、仍被知识库引用的策略不可删（批量时跳过或全部失败时报冲突原因）

## 2026-09-10（文件名列表截断展示）

- 文件管理「文档名称」列：展示前 12 字符 + `…`，鼠标悬停 tooltip 显示完整名称

## 2026-09-10（文件管理 + 片段管理拆分）

- 上传改为**仅落盘 + 绑知识库**（`UPLOADED`），不再自动 ingest
- 文件管理：解析 / 切分（选策略写 MySQL `t_knowledge_segment`）/ 向量化 / 清向量 / 下载 / 重传 / 修改 / 删除
- 片段管理页启用：筛选（知识库/文档/类型/内容）+ 分页 + 编辑/删除/批量删；权威数据在 MySQL，PG 仅存向量
- AI：`POST /api/v1/ai/chunk/split`、`POST /api/v1/ai/chunk/embed`；`POST .../segments/delete-vectors`
- 侧栏：「① 文件管理」+「文件管理」/「片段管理」；路由 `/knowledge/document`、`/knowledge/segment`
- 旧仅 PG 片段的文档：列表可能仍显示向量状态，片段管理为空，需重新切分

## 2026-09-10（文档管理列对齐 + 解析预览）

- 文档列表列：文档名称 / 所属知识库 / 类型 / 大小 / 解析状态 / 向量化状态 / 字符数 / 上传时间 / 操作
- 新增「解析」：`GET /api/v1/knowledge/documents/{id}/parsed-text` → AI `POST /api/v1/ai/parse/extract`（PDF/TXT/MD/DOCX → 纯文本弹窗）
- 解析后回写 `meta.charCount`；入库结果增加 `char_count`

## 2026-09-10（切分策略对齐参考数据 + 列表标题）

- 种子对齐：`递归分块-通用`（默认）/ `递归分块-短片段` / `父子分块-制度手册`；脚本 `07-chunk-strategy-align.sql`；下线 `cs_fixed`
- 列表列名同步：策略名称、策略类型、片段长度、重叠字符、父块长度、分隔符、默认、备注

## 2026-09-10（路由空白 + 固定长度策略）

- AdminLayout：去掉 `transition mode=out-in`（懒加载切换会空白卡住）；侧栏在切分策略↔文档管理间可正常切换
- 种子补第三条 `cs_fixed`（固定长度 FIXED）；脚本 `06-chunk-strategy-fixed.sql`；类型下拉含固定长度

## 2026-09-10（文档页空白 + 切分策略操作列）

- 文档管理改为路由懒加载；图标 `Document` 改名避免与 DOM 冲突；列表数据做数组兜底
- 切分策略：「默认」列改为勾选设默认；操作列仅保留编辑/删除圆形按钮

## 2026-09-10（切分策略 CRUD + 文档/知识库联动）

- DB：`t_chunk_strategy.is_default`；种子补 `separators_json`；`cs_recursive` 为默认；增量脚本 `05-chunk-strategy-default.sql`
- Java：`/api/v1/knowledge/chunk-strategies` 启用列表 / 分页 CRUD / 设默认 / 被 KB 引用不可删；旧 `/strategies/chunk` 转发启用列表
- 上传 multipart 增加 `chunkStrategyId`；解析优先级：上传指定 → KB 绑定 → 系统默认；`meta_json` 写入策略与参数；AI multipart 传 `strategy_type` / 父子尺寸 / `separators_json`
- Python：`RECURSIVE` 递归分隔符 + 滑窗兜底；`PARENT_CHILD` 父→子，**仅子块入向量**（带 `parent_segment_id`）
- Admin：切分策略页完整 CRUD；文档上传步骤 3 选策略；知识库表单绑定 `chunkStrategyId`

## 2026-09-10（入库 Embedding 批次超限）

- 根因：百炼要求 `input.contents` 单批 ≤10，代码 `batch_size=16` 触发 400 → 入库 502
- `dashscope_embed.embed_texts` 改为每批最多 10 条

## 2026-09-10（新增知识库：分类下拉被遮罩挡住）

- 根因：`AppModal` z-index=4000，高于 Element Plus 默认 select popper，下拉层在遮罩下表现为「不可选」
- 表单/分类管理内 `el-select` 增加 `popper-class=app-modal-select-popper`（z-index 4200）
- 新增知识库弹窗加宽至 720px；描述框 `rows=8` + 最小高度约 160px

- 新表 `t_knowledge_category` + 种子 enterprise/policy；脚本 `04-knowledge-category.sql`
- Java：`/api/v1/knowledge/categories` 启用列表 / 分页 CRUD；被知识库引用不可删；改 code 同步 KB
- 知识库页：顶栏「分类管理」；表单分类改下拉；列表分类列 + 筛选
- 知识库分页支持 `category` 查询参数

## 2026-09-10（知识库操作列去掉文档/上传）

- 操作列移除「文档」「上传」（与文档数点击、向量状态下钻重复）；保留向量状态 / 编辑 / 删除
- 仍可通过「文档数」进入文档管理并带上该库筛选

## 2026-09-10（向量汇总 22 ≠ 明细 11）

- 根因：PG 残留已不在 MySQL 的孤儿 `document_id`（10+1=11）；库级汇总含孤儿，文档表只叠存活文档
- Java 汇总改为只计 `deleted=0` 文档；加载时顺带 `DELETE` 孤儿向量
- AI：`DELETE /api/v1/ai/knowledge/documents/{id}/vectors`
- 向量弹窗汇总改为按下方文档明细求和，口径一致

- 真因：`DocumentPage.vue` 使用了 `AppModal` / `AppPagination` 但未 `import`，Vue 当作未知标签，槽位内容直接内联在页面底部（表现为只露出「1 知识库」）
- 已补：`import { AppModal, AppPagination } from '@/components/common'`
- `AppModal` 仍为自研 Teleport 遮罩（不依赖 el-dialog）

## 2026-09-10（上传弹窗裁切 — 加固）

- 根因：Element Plus 仅在 `appendToBody=true` 时 Teleport 到 body；默认弹窗留在页面内，再叠加 `.panel { overflow:hidden }` 与 layout `transform` 过渡，表现为底部只露出「1 知识库」
- `AppModal`：外层 `Teleport` + `:append-to-body="true"` + 居中 + z-index 4000
- 文档/知识库页：弹窗移出 `.panel`；去掉 panel `overflow:hidden`
- `AdminLayout` 路由过渡去掉 `transform`，避免 `position:fixed` 错位

## 2026-09-10（上传弹窗被页面裁切）

- `AppModal` 增加 `append-to-body`，避免文档页 `.panel { overflow: hidden }` 把弹窗裁成底部只剩「知识库」一块

## 2026-09-10（统一时间格式）

- 前后端统一为 `yyyy-MM-dd HH:mm:ss`（去掉 ISO 的 `T` 与毫秒）
- 前端 `utils/datetime.ts`；Java `DateTimes`；文档列表创建时间列补格式化

## 2026-09-10（向量状态改读 PGVector）

- 根因：片段只在 PG，MySQL `t_knowledge_segment` 为空，统计恒为 0；且文档列表用库表计数覆盖了 `meta_json.segmentCount`
- AI：`GET /api/v1/ai/knowledge/bases/{id}/vector-status` 从 BM25/向量表汇总
- Java 知识库/文档列表与向量下钻改为合并 PG 真实计数

## 2026-09-10（向量状态 ↔ 文档管理联动）

- 知识库「向量状态」下钻文档明细；统计项/文档可跳转文档管理（`parseStatus` / `vectorIssue` / `documentId`）
- 文档列表增加向量状态列；支持 `vectorIssue=PENDING|FAILED|PARTIAL` 筛选；URL `documentId` 自动打开详情
- Java：`GET /bases/{id}/vector-documents`；文档分页返回向量计数并支持 `vectorIssue`

## 2026-09-10（知识库 ↔ 文档管理联动）

- 知识库页：文档数可点；操作增加「文档 / 上传」，跳转文档页并带上 `knowledgeBaseId`（上传再带 `upload=1`）
- 文档页：读取 URL 筛选并同步；顶栏展示当前库；知识库列回跳知识库管理并高亮

## 2026-09-09（知识库管理 CRUD）

- Admin `/knowledge/base`：筛选（名称/状态）+ 新增/编辑/删除/批量删除 + 分页 + 向量状态弹窗
- Java：`GET /bases/page`、`POST/PUT/DELETE`、`POST /batch-delete`；列表带文档数/片段数/向量汇总
- 删库软删文档与片段，并调 AI `DELETE .../bases/{id}/vectors` 清 PGVector
- 保留 `GET /bases` 启用列表供文档上传下拉

## 2026-09-09（公共分页 + 弹窗壳抽取）

- 抽出 `AppPagination` / `usePagination`（含客户端切片）；文档列表与详情片段改用公共分页
- 模型配置、切分策略、检索策略列表补充分页（全量接口暂客户端分页）
- 抽出 `AppModal`（`form` / `detail`）+ `app-modal.css`；文档「上传入库 / 详情」改用公共弹窗样式壳

## 2026-09-09（详情分页与表格重叠修复）

- 片段表改为占满中间剩余高度（`height: 100%`），不再用固定像素高度顶到分页区
- 去掉全宽底边缩放条；分页区独立底栏并预留右下角拖拽空间

## 2026-09-09（详情弹窗可拖拽缩放）

- 支持鼠标拖拽右边 / 底边 / 右下角调整宽高；片段表高度随弹窗联动

## 2026-09-09（详情翻页固定高度；片段列加宽）

- 详情弹窗 body / 片段表固定高度，翻页不再缩小
- 片段内容列 `min-width: 520`，预览加长

## 2026-09-09（详情：文档名称替换 ID；片段列缩短）

- 摘要区「文档 ID」改为「文档名称」，不再展示 ID
- 片段内容列缩短；「展开/收起」改为右侧小胶囊按钮

## 2026-09-09（详情摘要区样式再平衡）

- 去掉挤成一行的字段；改为三列轻量信息格（标题独立、切分参数合并展示）
- 时间格式化为可读样式；弹窗标题简化为「文档详情」

## 2026-09-09（详情元信息改为紧凑摘要条）

- 去掉大卡片网格；标题+状态+关键信息横排紧凑展示，给片段列表留更多空间

## 2026-09-09（详情加高重排；列表/详情展示切分参数）

- 详情弹窗约 1100px / 更高；标题区 + 卡片元信息 + 片段面板分区
- 列表与详情展示 `chunkSize` / `chunkOverlap`（从 `meta_json` 回填；新上传失败也会落库）

## 2026-09-09（详情弹窗放大；片段默认折叠）

- 详情弹窗加宽至约 960px；元信息四列卡片排版
- 片段内容默认一行预览，点击展开/收起全文
- 列表「详情」改为实心小按钮

## 2026-09-09（文档详情弹窗：片段分页+搜索；标题换行缩小）

- 操作列「详情」→ 弹窗列出 PG 全量片段；支持关键词搜索与分页
- 链路：`GET /api/v1/knowledge/documents/{id}/segments` → AI `.../ai/knowledge/documents/{id}/segments`
- 标题列过长时缩小字号并换行

## 2026-09-09（列表默认 8 条；状态中文）

- 文档列表默认 `pageSize=8`
- 筛选与表格状态展示中文（已就绪 / 失败 / 解析中 / 已上传…）
- 弹窗步骤序号样式调整；开始入库按钮去掉「4.」

## 2026-09-09（上传弹窗放大+序号；文档列表服务端分页）

- 上传弹窗加宽至约 760px，步骤 1/2/3 序号；底部「4. 开始入库」
- `GET /api/v1/knowledge/documents` 返回 `PageResult`（`page`/`pageSize`/`total`/`records`）
- 列表底部分页：总数、每页条数、翻页

## 2026-09-09（文档页：列表查询 + 上传弹窗选知识库）

- 主页面仅列表；查询：知识库 / 标题 / 状态
- 「上传入库」弹窗：知识库下拉（N:1）+ 选文件 + 切分参数 + 开始入库
- 新增 `GET /api/v1/knowledge/bases`；文档列表支持筛选并回填 `knowledgeBaseName`
- 种子增加 `kb_policy`（制度政策库）

## 2026-09-09（登录：去掉本地放行死循环）

- 根因：Service `:8081` 宕机 → Gateway 登录 500 → 前端本地 `local-*` 放行 → 文档页又因无 JWT 踢回登录
- 已重启 Service；登录**仅走 Java JWT**，失败时明确提示检查 8080/8081
- 进入登录页自动清除残留 `local-*` token

## 2026-09-09（文档列表 403 空响应 → JSON 解析失败）

- 根因：本地演示 token（`local-*`）过不了 Java JWT，Security 返回 **空 body 403**，前端 `res.json()` 报 Unexpected end of JSON input
- 前端 `apiGet/apiPost` 改为先读 text 再解析，401/403 给出明确重登提示
- 文档页检测 `local-*` token 并引导重新登录
- Java `SecurityConfig` 401/403 统一返回 JSON `Result`

## 2026-09-09（白屏修复：去掉循环依赖与缓存戳）

- 占位页不再内嵌 `DocumentPage`（避免循环依赖导致主内容空白）
- 去掉 `main.ts?v=…` 查询串；登录页去掉嵌套 `<form>`
- `App.vue` 捕获渲染错误并显示提示

## 2026-09-09（根因：src 旁路旧 .js 覆盖路由）

- 删除 `frontend/admin/src/**/*.js` 旁路编译产物（含 `router/index.js` 仍指向 Placeholder、「文档管理」）
- Vite 解析时 `.js` 优先于 `.ts`，导致改 TS 不生效；`tsconfig` 加 `noEmit: true` 防再写出

- 工作台不再用假账号打 `/auth/login` 探测（会刷 401）
- 改为公开 `GET /api/v1/ping`
- 根路径默认进入 `/dashboard` 工作台

- 去掉顶栏三个绿色快捷按钮
- `/knowledge/document` 改为同步组件加载，避免仍落到占位页
- 上传页提供：选择文件、chunkSize/chunkOverlap、开始入库、文档列表
- Java/Python ingest 支持可选切分参数

### 正式链路

`Admin → Gateway → Java`：`POST /api/v1/knowledge/documents/upload`  
→ 落盘 `notemind.storage.upload-dir` → MySQL `UPLOADED/PARSING/READY|FAILED`  
→ `AiEngineHttpClient` → `POST /api/v1/ai/ingest/documents/{documentId}` → PGVector  

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/knowledge/documents/upload` | multipart `file` + `knowledgeBaseId`（默认 `kb_default`） |
| `GET /api/v1/knowledge/documents` | 文档列表 |
| `GET /api/v1/knowledge/documents/{id}` | 详情（含 `segmentCount`） |
| `POST /api/v1/ai/ingest/documents/{document_id}` | AI 按文档 ID 入库；`/upload` 仍可用 |

默认知识库统一为 **`kb_default`**（检索/问答前端默认同步）。管理端文档页已改走 Gateway，不再直连 AI 上传。

---

## 2026-09-09

### 管理端可测（Admin :5174）

| 页面 | 路径 | 能力 |
| --- | --- | --- |
| 登录 | `/login` | 单一「登录」：优先 Java JWT，不可达时演示账号本地放行 |
| 工作台 | `/dashboard` | 健康探测 + 四个可测入口按钮 |
| 文档上传 | `/knowledge/document` | Gateway → Java → AI ingest；列表 + READY/FAILED |
| 检索测试 | `/knowledge/retrieval-test` | `POST /api/v1/ai/retrieval/test`（KB=`kb_default`） |
| 问答测试 | `/app` | SSE chat + sources（KB=`kb_default`） |
| 模型配置 | `/ai/model` | `GET /api/v1/ai-models`（需 JWT） |
| 检索策略 | `/knowledge/retrieval-strategy` | `GET /api/v1/strategies/retrieval` |
| 切分策略 | `/knowledge/chunk-strategy` | `GET /api/v1/strategies/chunk` |

侧栏新增分组 **「联调演示」**，与仍为占位的菜单区分。

### 平台 / 引擎

- Java：JWT 登录、Gateway CORS、模型/策略列表、种子 `scripts/db/03-seed-config.sql`
- Python：ingest / retrieval / chat SSE 已冒烟（上传样例 → sources）
- Vite：`/api/v1/ai`、`/health` → `:8000`；其余 `/api` → `:8080`

### 推荐验收路径

1. 打开 http://127.0.0.1:5174/login  
2. 「登录」或「仅测 AI」  
3. 工作台按 1→2→3：上传 `docs/file` 样例 → 检索 → 问答看引用  

### 仍为占位（未接业务）

用户管理、知识库 CRUD、片段管理、召回调试台、Prompt/工具/Agent、对话日志、评测三页、个人资料。
