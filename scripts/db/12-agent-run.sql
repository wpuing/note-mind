-- Agent 运行表补充字段
USE notemind;

SET @db := DATABASE();

SET @e1 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='error_message');
SET @s1 := IF(@e1=0, 'ALTER TABLE t_agent_run ADD COLUMN error_message VARCHAR(1024) NULL COMMENT ''失败原因'' AFTER final_answer', 'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @e2 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='rewrite_rounds');
SET @s2 := IF(@e2=0, 'ALTER TABLE t_agent_run ADD COLUMN rewrite_rounds INT NOT NULL DEFAULT 0 COMMENT ''改写重试次数'' AFTER retrieval_rounds', 'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @e3 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='knowledge_base_id');
SET @s3 := IF(@e3=0, 'ALTER TABLE t_agent_run ADD COLUMN knowledge_base_id VARCHAR(32) NULL COMMENT ''知识库'' AFTER session_id', 'SELECT 1');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

SET @e4 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='run_type');
SET @s4 := IF(@e4=0, 'ALTER TABLE t_agent_run ADD COLUMN run_type VARCHAR(32) NOT NULL DEFAULT ''AGENTIC_QA'' COMMENT ''运行类型'' AFTER knowledge_base_id', 'SELECT 1');
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;

SET @e5 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='sources_json');
SET @s5 := IF(@e5=0, 'ALTER TABLE t_agent_run ADD COLUMN sources_json JSON NULL COMMENT ''引用来源'' AFTER rewrite_rounds', 'SELECT 1');
PREPARE st5 FROM @s5; EXECUTE st5; DEALLOCATE PREPARE st5;

SET @e6 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_agent_run' AND COLUMN_NAME='conclusion_label');
SET @s6 := IF(@e6=0, 'ALTER TABLE t_agent_run ADD COLUMN conclusion_label VARCHAR(64) NULL COMMENT ''正常回答/未找到等'' AFTER sources_json', 'SELECT 1');
PREPARE st6 FROM @s6; EXECUTE st6; DEALLOCATE PREPARE st6;
