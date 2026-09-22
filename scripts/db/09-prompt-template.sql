-- Prompt 模板：场景字段 + 对齐外部系统种子
USE notemind;

SET @db := DATABASE();
SET @exist := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_prompt_template' AND COLUMN_NAME = 'scenario'
);
SET @sql := IF(
  @exist = 0,
  'ALTER TABLE t_prompt_template ADD COLUMN scenario VARCHAR(64) NULL COMMENT ''使用场景：问答生成/查询改写/Agentic RAG/评测集构建/效果评测'' AFTER name',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
  remark = VALUES(remark),
  deleted = 0,
  delete_time = NULL;
