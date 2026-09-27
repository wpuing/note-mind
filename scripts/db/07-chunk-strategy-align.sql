-- 切分策略种子对齐参考系统（递归通用 / 递归短片段 / 父子制度手册）
USE notemind;

-- 递归分块-通用（默认，保持 id=cs_recursive 供知识库引用）
UPDATE t_chunk_strategy
SET name = '递归分块-通用',
    strategy_type = 'RECURSIVE',
    chunk_size = 500,
    chunk_overlap = 50,
    parent_chunk_size = NULL,
    child_chunk_size = NULL,
    child_overlap = NULL,
    separators_json = JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''),
    enabled = 1,
    is_default = 1,
    remark = NULL,
    deleted = 0,
    delete_time = NULL,
    update_time = CURRENT_TIMESTAMP(3)
WHERE id = 'cs_recursive';

-- 父子分块-制度手册
UPDATE t_chunk_strategy
SET name = '父子分块-制度手册',
    strategy_type = 'PARENT_CHILD',
    chunk_size = 300,
    chunk_overlap = 30,
    parent_chunk_size = 1200,
    child_chunk_size = 300,
    child_overlap = 30,
    separators_json = JSON_ARRAY('\n\n', '\n', '。', '；', '，', ',', ' ', ''),
    enabled = 1,
    is_default = 0,
    remark = '子块用于检索，命中后回填父块给模型，适合条款型文档。',
    deleted = 0,
    delete_time = NULL,
    update_time = CURRENT_TIMESTAMP(3)
WHERE id = 'cs_parent_child';

-- 递归分块-短片段（新建；若曾用 cs_fixed 则一并清理）
INSERT INTO t_chunk_strategy (
  id, name, strategy_type, chunk_size, chunk_overlap, parent_chunk_size, child_chunk_size, child_overlap,
  separators_json, enabled, is_default, remark
) VALUES (
  'cs_recursive_short', '递归分块-短片段', 'RECURSIVE', 250, 30, NULL, NULL, NULL,
  JSON_ARRAY('\n\n', '\n', '。', '！', '？', '.', '!', '?', ' ', ''),
  1, 0, '片段更短，检索更聚焦，适合问答型资料。'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  strategy_type = VALUES(strategy_type),
  chunk_size = VALUES(chunk_size),
  chunk_overlap = VALUES(chunk_overlap),
  parent_chunk_size = VALUES(parent_chunk_size),
  child_chunk_size = VALUES(child_chunk_size),
  child_overlap = VALUES(child_overlap),
  separators_json = VALUES(separators_json),
  enabled = VALUES(enabled),
  is_default = VALUES(is_default),
  remark = VALUES(remark),
  deleted = 0,
  delete_time = NULL;

-- 仅保留默认在 cs_recursive
UPDATE t_chunk_strategy SET is_default = 0 WHERE id <> 'cs_recursive' AND deleted = 0;

-- 下线此前误加的固定长度种子（若存在）
UPDATE t_chunk_strategy
SET deleted = 1, delete_time = CURRENT_TIMESTAMP(3), is_default = 0, update_time = CURRENT_TIMESTAMP(3)
WHERE id = 'cs_fixed' AND deleted = 0;
