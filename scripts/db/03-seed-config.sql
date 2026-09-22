-- NoteMind 阶段 1 种子：模型 / Prompt / 切分 / 检索策略 / 默认知识库
USE notemind;

INSERT INTO t_ai_model_config (
  id, name, model_type, provider, model_name, base_url, api_style, dimension, temperature, enabled, remark
) VALUES
('m_chat_qwen', '百炼对话模型配置', 'CHAT', '阿里云百炼', 'qwen-plus',
 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'openai_compatible', NULL, 0.20, 1, '用于问答生成'),
('m_emb_v4', '百炼向量模型配置', 'EMBEDDING', '阿里云百炼', 'text-embedding-v4',
 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'openai_compatible', 1024, NULL, 1, '用于知识片段向量化，维度必须与 PGVector 建表时一致。'),
('m_rerank_gte', '百炼重排模型配置', 'RERANK', '阿里云百炼', 'gte-rerank-v2',
 'https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank', 'dashscope_native', NULL, NULL, 1, '用于检索结果重排')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  provider = VALUES(provider),
  model_name = VALUES(model_name),
  base_url = VALUES(base_url),
  api_style = VALUES(api_style),
  dimension = VALUES(dimension),
  enabled = VALUES(enabled),
  remark = VALUES(remark);

INSERT INTO t_prompt_template (
  id, code, name, scenario, content, variables_json, enabled, remark
) VALUES
(
  'p_rag_answer', 'RAG_ANSWER', '知识库问答', '问答生成',
  '你是企业知识库助手。请严格根据下面提供的资料回答用户问题。资料中没有的内容不要编造，直接回答"知识库中未找到相关内容"。\n\n资料：\n{context}\n\n用户问题：{question}',
  JSON_ARRAY('context', 'question'), 1,
  '主问答 Prompt，要求模型不得脱离资料作答。'
),
(
  'p_query_rewrite', 'QUERY_REWRITE', '查询改写', '查询改写',
  '请把用户问题改写成 {count} 个表述不同但语义一致的检索问题，覆盖可能的同义表达。\n只输出问题本身，每行一个，不要编号。\n用户问题：{question}',
  JSON_ARRAY('count', 'question'), 1,
  '检索前查询改写，用于提升召回。'
),
(
  'p_agent_plan', 'AGENT_PLAN', 'Agent 规划', 'Agentic RAG',
  '你是 Agentic RAG 规划助手。根据用户问题与历史，决定下一步动作。\n历史：{history}\n问题：{question}\n仅输出简洁规划说明。',
  JSON_ARRAY('history', 'question'), 1,
  'Agent 步骤规划提示词。'
),
(
  'p_eval_case_gen', 'EVAL_CASE_GEN', '评测用例生成', '评测集构建',
  '请根据以下资料生成 {count} 条问答对，用于 RAG 效果评测。每条包含 question 与 answer。\n资料：\n{context}',
  JSON_ARRAY('count', 'context'), 1,
  '从知识片段自动生成评测问答。'
),
(
  'p_hallucination', 'HALLUCINATION_CHECK', '幻觉校验', '效果评测',
  '判断模型回答是否忠实于资料。资料：\n{context}\n\n回答：\n{answer}\n\n仅输出：忠实 / 部分幻觉 / 严重幻觉，并给一句理由。',
  JSON_ARRAY('context', 'answer'), 1,
  'Judge：幻觉检测，temperature 建议 0。'
),
(
  'p_hyde', 'HYDE', 'HyDE 假想文档', '查询改写',
  '针对用户问题，写一段可能包含答案的短文档（不要解释过程）。\n问题：{question}',
  JSON_ARRAY('question'), 1,
  'HyDE：用假想答案做向量检索。'
)
ON DUPLICATE KEY UPDATE
  code = VALUES(code),
  name = VALUES(name),
  scenario = VALUES(scenario),
  content = VALUES(content),
  variables_json = VALUES(variables_json),
  enabled = VALUES(enabled),
  remark = VALUES(remark);

INSERT INTO t_chunk_strategy (
  id, name, strategy_type, chunk_size, chunk_overlap, parent_chunk_size, child_chunk_size, child_overlap,
  separators_json, enabled, is_default, remark
) VALUES
('cs_recursive', '递归分块-通用', 'RECURSIVE', 500, 50, NULL, NULL, NULL,
 JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''), 1, 1, NULL),
('cs_recursive_short', '递归分块-短片段', 'RECURSIVE', 250, 30, NULL, NULL, NULL,
 JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''), 1, 0, '片段更短，检索更聚焦，适合问答型资料。'),
('cs_parent_child', '父子分块-制度手册', 'PARENT_CHILD', 300, 30, 1200, 300, 30,
 JSON_ARRAY('\n\n', '\n', '。', '；', '，', ',', ' ', ''), 1, 0, '子块用于检索，命中后回填父块给模型，适合条款型文档。')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  strategy_type = VALUES(strategy_type),
  chunk_size = VALUES(chunk_size),
  chunk_overlap = VALUES(chunk_overlap),
  parent_chunk_size = VALUES(parent_chunk_size),
  child_chunk_size = VALUES(child_chunk_size),
  child_overlap = VALUES(child_overlap),
  separators_json = VALUES(separators_json),
  is_default = VALUES(is_default),
  remark = VALUES(remark);

INSERT INTO t_retrieval_strategy (
  id, name,
  enable_vector, enable_bm25, enable_rrf, enable_rerank, enable_rewrite, enable_parent_fill,
  top_k, rerank_top_n, vector_top_k, bm25_top_k, rrf_k,
  cosine_threshold, rerank_threshold, rewrite_mode, rewrite_count,
  enabled, is_default, remark
) VALUES
(
  'rs_baseline_vector', '基线-纯向量检索',
  1, 0, 0, 0, 0, 0,
  5, 4, 5, 0, 60,
  0.3000, NULL, 'multi_query', 3,
  1, 1, '仅向量召回；余弦阈值过滤。相关片段实测 0.46~0.77，无关 0.14~0.23，建议 0.3'
),
(
  'rs_hybrid', '进阶-混合检索',
  1, 1, 1, 0, 0, 0,
  8, 4, 8, 8, 60,
  0.3000, NULL, 'multi_query', 3,
  1, 0, '向量加 BM25 双路召回后 RRF 融合，解决专有名词检索不到的问题。RRF 名次分不做阈值过滤'
),
(
  'rs_full', '完整-混合检索加重排加改写',
  1, 1, 1, 1, 1, 1,
  4, 4, 10, 10, 60,
  0.3000, 0.0500, 'multi_query', 3,
  1, 0, '全部能力打开，用于和前两套策略做效果对比'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  enable_vector = VALUES(enable_vector),
  enable_bm25 = VALUES(enable_bm25),
  enable_rrf = VALUES(enable_rrf),
  enable_rerank = VALUES(enable_rerank),
  enable_rewrite = VALUES(enable_rewrite),
  enable_parent_fill = VALUES(enable_parent_fill),
  top_k = VALUES(top_k),
  rerank_top_n = VALUES(rerank_top_n),
  vector_top_k = VALUES(vector_top_k),
  bm25_top_k = VALUES(bm25_top_k),
  rrf_k = VALUES(rrf_k),
  cosine_threshold = VALUES(cosine_threshold),
  rerank_threshold = VALUES(rerank_threshold),
  rewrite_mode = VALUES(rewrite_mode),
  rewrite_count = VALUES(rewrite_count),
  enabled = VALUES(enabled),
  is_default = VALUES(is_default),
  remark = VALUES(remark);

INSERT INTO t_knowledge_category (id, code, name, sort_no, status) VALUES
('cat_enterprise', 'enterprise', '企业资料', 10, 1),
('cat_policy', 'policy', '制度政策', 20, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_no = VALUES(sort_no), status = VALUES(status);

INSERT INTO t_knowledge_base (
  id, name, category, description, embedding_model_id, chunk_strategy_id, retrieval_strategy_id, status
) VALUES (
  'kb_default', '默认知识库', 'enterprise', '本地联调默认库', 'm_emb_v4', 'cs_recursive', 'rs_baseline_vector', 1
), (
  'kb_policy', '制度政策库', 'policy', '制度 / 办法 / 白皮书等', 'm_emb_v4', 'cs_recursive', 'rs_baseline_vector', 1
) ON DUPLICATE KEY UPDATE name = VALUES(name), retrieval_strategy_id = VALUES(retrieval_strategy_id);

INSERT INTO t_qa_app (
  id, name, description, knowledge_base_id, retrieval_strategy_id, chat_model_id, answer_prompt_id, enable_agentic, enabled
) VALUES (
  'app_default', '企业知识问答', '阶段4演示', 'kb_default', 'rs_baseline_vector', 'm_chat_qwen', 'p_rag_answer', 0, 1
) ON DUPLICATE KEY UPDATE name = VALUES(name), retrieval_strategy_id = VALUES(retrieval_strategy_id);
