-- 切分策略：默认标志 + 分隔符种子
USE notemind;

-- 兼容已存在库：无列则添加
SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_chunk_strategy'
    AND COLUMN_NAME = 'is_default'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_chunk_strategy ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 COMMENT ''1=系统默认策略'' AFTER enabled',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_chunk_strategy
SET separators_json = JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''),
    is_default = 1,
    remark = '默认递归切分'
WHERE id = 'cs_recursive' AND deleted = 0;

UPDATE t_chunk_strategy
SET separators_json = JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''),
    is_default = 0,
    remark = '子块检索，命中后可回填父块'
WHERE id = 'cs_parent_child' AND deleted = 0;

UPDATE t_chunk_strategy SET is_default = 0 WHERE id <> 'cs_recursive' AND deleted = 0;
