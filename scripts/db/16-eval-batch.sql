-- 批量评测：补齐任务进度字段 + Judge Prompt
USE notemind;

SET @db := DATABASE();

SET @e1 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='task_no');
SET @s1 := IF(@e1=0, 'ALTER TABLE t_eval_report ADD COLUMN task_no BIGINT NULL COMMENT ''展示用任务号'' AFTER id', 'SELECT 1');
PREPARE stmt FROM @s1; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @e2 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='done_count');
SET @s2 := IF(@e2=0, 'ALTER TABLE t_eval_report ADD COLUMN done_count INT NOT NULL DEFAULT 0 COMMENT ''已完成用例数'' AFTER case_count', 'SELECT 1');
PREPARE stmt FROM @s2; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @e3 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='fail_reason');
SET @s3 := IF(@e3=0, 'ALTER TABLE t_eval_report ADD COLUMN fail_reason VARCHAR(1024) NULL COMMENT ''任务失败原因'' AFTER overall_score', 'SELECT 1');
PREPARE stmt FROM @s3; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @e4 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='duration_ms');
SET @s4 := IF(@e4=0, 'ALTER TABLE t_eval_report ADD COLUMN duration_ms BIGINT NULL COMMENT ''总耗时毫秒'' AFTER fail_reason', 'SELECT 1');
PREPARE stmt FROM @s4; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @e5 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='dataset_name');
SET @s5 := IF(@e5=0, 'ALTER TABLE t_eval_report ADD COLUMN dataset_name VARCHAR(128) NULL COMMENT ''评测集名称快照'' AFTER dataset_id', 'SELECT 1');
PREPARE stmt FROM @s5; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @e6 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND COLUMN_NAME='strategy_name');
SET @s6 := IF(@e6=0, 'ALTER TABLE t_eval_report ADD COLUMN strategy_name VARCHAR(128) NULL COMMENT ''检索策略名称快照'' AFTER retrieval_strategy_id', 'SELECT 1');
PREPARE stmt FROM @s6; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填任务号（已有行）
UPDATE t_eval_report
SET task_no = CONV(RIGHT(REPLACE(id, '-', ''), 8), 16, 10) % 900000 + 100000
WHERE task_no IS NULL;

-- 唯一索引（忽略已存在）
SET @ix := (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_report' AND INDEX_NAME='uk_eval_report_task_no');
SET @sx := IF(@ix=0, 'ALTER TABLE t_eval_report ADD UNIQUE KEY uk_eval_report_task_no (task_no)', 'SELECT 1');
PREPARE stmt FROM @sx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO t_prompt_template (
  id, code, name, scenario, content, variables_json, enabled, remark
) VALUES
(
  'p_eval_ctx_recall', 'EVAL_CONTEXT_RECALL', '上下文召回评测', '效果评测',
  '你是 RAG 评测裁判（temperature=0）。判断：标准答案中的关键信息，是否能从检索资料中找到依据。\n问题：{question}\n标准答案：{expected}\n检索资料：\n{context}\n\n只输出 JSON：{"score":0或1,"reason":"一句理由"}。score=1 表示标准答案要点已被资料覆盖。',
  JSON_ARRAY('question', 'expected', 'context'), 1,
  'Context Recall Judge'
),
(
  'p_eval_ctx_precision', 'EVAL_CONTEXT_PRECISION', '上下文精度评测', '效果评测',
  '你是 RAG 评测裁判（temperature=0）。对每条检索资料判断是否对回答问题/对齐标准答案有用。\n问题：{question}\n标准答案：{expected}\n资料列表（按排名）：\n{context}\n\n只输出 JSON：{"scores":[0或1,...],"reason":"一句理由"}。scores 与资料条数一一对应，1=有用。',
  JSON_ARRAY('question', 'expected', 'context'), 1,
  'Context Precision Judge'
),
(
  'p_eval_faithfulness', 'EVAL_FAITHFULNESS', '忠实度评测', '效果评测',
  '你是 RAG 评测裁判（temperature=0）。判断生成答案是否忠实于资料（有无编造）。\n问题：{question}\n资料：\n{context}\n\n生成答案：\n{answer}\n\n只输出 JSON：{"score":0到1的小数,"reason":"一句理由"}。1=完全忠实，0=严重幻觉。',
  JSON_ARRAY('question', 'context', 'answer'), 1,
  'Faithfulness Judge'
),
(
  'p_eval_relevancy', 'EVAL_ANSWER_RELEVANCY', '答案相关性评测', '效果评测',
  '你是 RAG 评测裁判（temperature=0）。判断生成答案是否在回答该问题（有无跑题）。\n问题：{question}\n生成答案：\n{answer}\n\n只输出 JSON：{"score":0到1的小数,"reason":"一句理由"}。',
  JSON_ARRAY('question', 'answer'), 1,
  'Answer Relevancy Judge'
),
(
  'p_eval_rel_questions', 'EVAL_RELEVANCY_QUESTIONS', '答案反推问题', '效果评测',
  '根据下面的答案，反推出 3 个用户可能提出的问题。每行一个问题，不要编号。\n答案：\n{answer}',
  JSON_ARRAY('answer'), 1,
  'Answer Relevancy 辅助：从答案反推问题'
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
