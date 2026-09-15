-- 问答应用补充：历史条数、兜底话术
USE notemind;

SET @db := DATABASE();

SET @e1 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_qa_app' AND COLUMN_NAME='history_limit');
SET @s1 := IF(@e1=0, 'ALTER TABLE t_qa_app ADD COLUMN history_limit INT NOT NULL DEFAULT 6 COMMENT ''携带历史消息条数'' AFTER enable_agentic', 'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @e2 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_qa_app' AND COLUMN_NAME='fallback_reply');
SET @s2 := IF(@e2=0, 'ALTER TABLE t_qa_app ADD COLUMN fallback_reply VARCHAR(512) NULL COMMENT ''未命中时兜底话术'' AFTER history_limit', 'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

-- 演示应用（幂等：按 id 存在则跳过）
INSERT INTO t_qa_app (
  id, create_time, update_time, deleted,
  name, description, knowledge_base_id, retrieval_strategy_id,
  chat_model_id, answer_prompt_id, enable_agentic, history_limit, fallback_reply, enabled
)
SELECT
  'app_after_sales', NOW(3), NOW(3), 0,
  '售后智能客服',
  '面向客服坐席和客户，回答质保、维修、备件与退换货问题，走 Agentic 链路自行判断要不要重新检索。',
  COALESCE((SELECT id FROM t_knowledge_base WHERE deleted=0 AND name LIKE '%售后%' LIMIT 1), 'kb_default'),
  COALESCE((SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND name LIKE '%进阶%' LIMIT 1), 'rs_hybrid'),
  'm_chat_qwen', 'p_rag_answer', 1, 6,
  '这个问题在售后知识库里没有查到，请转接人工客服 400-820-9600。',
  1
WHERE NOT EXISTS (SELECT 1 FROM t_qa_app WHERE id='app_after_sales');

INSERT INTO t_qa_app (
  id, create_time, update_time, deleted,
  name, description, knowledge_base_id, retrieval_strategy_id,
  chat_model_id, answer_prompt_id, enable_agentic, history_limit, fallback_reply, enabled
)
SELECT
  'app_policy', NOW(3), NOW(3), 0,
  '企业制度问答',
  '面向员工的制度与流程问答，直线链路检索生成。',
  COALESCE((SELECT id FROM t_knowledge_base WHERE deleted=0 AND name LIKE '%制度%' LIMIT 1), 'kb_default'),
  COALESCE((SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND is_default=1 LIMIT 1), 'rs_baseline_vector'),
  'm_chat_qwen', 'p_rag_answer', 0, 8,
  '制度库中暂未找到相关条款，请联系行政同事确认。',
  1
WHERE NOT EXISTS (SELECT 1 FROM t_qa_app WHERE id='app_policy');

INSERT INTO t_qa_app (
  id, create_time, update_time, deleted,
  name, description, knowledge_base_id, retrieval_strategy_id,
  chat_model_id, answer_prompt_id, enable_agentic, history_limit, fallback_reply, enabled
)
SELECT
  'app_product', NOW(3), NOW(3), 0,
  '产品技术助手',
  '产品参数与技术问题助手，Agentic 模式。',
  COALESCE((SELECT id FROM t_knowledge_base WHERE deleted=0 ORDER BY create_time DESC LIMIT 1), 'kb_default'),
  COALESCE((SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND name LIKE '%完整%' LIMIT 1),
           (SELECT id FROM t_retrieval_strategy WHERE deleted=0 LIMIT 1)),
  'm_chat_qwen', 'p_rag_answer', 1, 6,
  '产品资料中暂无相关说明，请联系技术支持。',
  1
WHERE NOT EXISTS (SELECT 1 FROM t_qa_app WHERE id='app_product');
