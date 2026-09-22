-- 评测集模块字段补充
USE notemind;

SET @db := DATABASE();

SET @e1 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_dataset' AND COLUMN_NAME='knowledge_base_id');
SET @s1 := IF(@e1=0, 'ALTER TABLE t_eval_dataset ADD COLUMN knowledge_base_id VARCHAR(32) NULL COMMENT ''关联知识库'' AFTER description', 'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @e2 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_case' AND COLUMN_NAME='source_type');
SET @s2 := IF(@e2=0, 'ALTER TABLE t_eval_case ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/FROM_DOC/FROM_DISLIKE'' AFTER document_id', 'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @e3 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_case' AND COLUMN_NAME='include_in_eval');
SET @s3 := IF(@e3=0, 'ALTER TABLE t_eval_case ADD COLUMN include_in_eval TINYINT NOT NULL DEFAULT 1 COMMENT ''是否参与评测'' AFTER source_type', 'SELECT 1');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

SET @e4 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_case' AND COLUMN_NAME='remark');
SET @s4 := IF(@e4=0, 'ALTER TABLE t_eval_case ADD COLUMN remark VARCHAR(512) NULL COMMENT ''备注'' AFTER include_in_eval', 'SELECT 1');
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;

SET @e5 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_case' AND COLUMN_NAME='source_content');
SET @s5 := IF(@e5=0, 'ALTER TABLE t_eval_case ADD COLUMN source_content MEDIUMTEXT NULL COMMENT ''来源片段原文快照'' AFTER remark', 'SELECT 1');
PREPARE st5 FROM @s5; EXECUTE st5; DEALLOCATE PREPARE st5;

SET @e6 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_eval_case' AND COLUMN_NAME='source_label');
SET @s6 := IF(@e6=0, 'ALTER TABLE t_eval_case ADD COLUMN source_label VARCHAR(256) NULL COMMENT ''片段定位说明'' AFTER source_content', 'SELECT 1');
PREPARE st6 FROM @s6; EXECUTE st6; DEALLOCATE PREPARE st6;

-- 放宽 source_type 默认（历史数据）
UPDATE t_eval_dataset SET source_type = 'MANUAL' WHERE source_type IS NULL OR source_type = '';
