-- 补齐固定长度切分策略（对齐常见三策略：递归 / 父子 / 固定长度）
USE notemind;

INSERT INTO t_chunk_strategy (
  id, name, strategy_type, chunk_size, chunk_overlap, parent_chunk_size, child_chunk_size, child_overlap,
  separators_json, enabled, is_default, remark
) VALUES (
  'cs_fixed', '固定长度切分', 'FIXED', 500, 50, NULL, NULL, NULL,
  NULL, 1, 0, '按固定窗口滑动切分，不依赖分隔符'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  strategy_type = VALUES(strategy_type),
  chunk_size = VALUES(chunk_size),
  chunk_overlap = VALUES(chunk_overlap),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  deleted = 0,
  delete_time = NULL;
