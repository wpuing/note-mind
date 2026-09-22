-- 对话日志：会话来源 + 反馈是否已沉淀
USE notemind;

SET @db := DATABASE();

SET @e1 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_chat_session' AND COLUMN_NAME='client_source');
SET @s1 := IF(@e1=0, 'ALTER TABLE t_chat_session ADD COLUMN client_source VARCHAR(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''ADMIN/USER'' AFTER title', 'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @e2 := (SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_chat_message' AND COLUMN_NAME='settled');
SET @s2 := IF(@e2=0, 'ALTER TABLE t_chat_message ADD COLUMN settled TINYINT NOT NULL DEFAULT 0 COMMENT ''反馈是否已沉淀'' AFTER feedback_reason', 'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;
