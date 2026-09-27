-- 检索策略：增列 + 知识库绑定 + 三套种子（对齐外部系统）
-- 可重复执行

-- ========== t_retrieval_strategy 增列 ==========
SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_retrieval_strategy' AND COLUMN_NAME = 'is_default'
    ),
    'SELECT 1',
    'ALTER TABLE t_retrieval_strategy ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 COMMENT ''1=系统默认策略'' AFTER enabled'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_retrieval_strategy' AND COLUMN_NAME = 'rewrite_count'
    ),
    'SELECT 1',
    'ALTER TABLE t_retrieval_strategy ADD COLUMN rewrite_count INT NOT NULL DEFAULT 3 COMMENT ''多查询扩展条数'' AFTER rewrite_mode'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_retrieval_strategy' AND COLUMN_NAME = 'rerank_top_n'
    ),
    'SELECT 1',
    'ALTER TABLE t_retrieval_strategy ADD COLUMN rerank_top_n INT NOT NULL DEFAULT 4 COMMENT ''重排保留条数'' AFTER top_k'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== t_knowledge_base.retrieval_strategy_id ==========
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_knowledge_base' AND COLUMN_NAME = 'retrieval_strategy_id'
    ),
    'SELECT 1',
    'ALTER TABLE t_knowledge_base ADD COLUMN retrieval_strategy_id VARCHAR(32) NULL COMMENT ''检索策略'' AFTER chunk_strategy_id'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_knowledge_base' AND INDEX_NAME = 'idx_kb_retrieval'
    ),
    'SELECT 1',
    'ALTER TABLE t_knowledge_base ADD KEY idx_kb_retrieval (retrieval_strategy_id)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 三套策略种子 ==========
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

-- 旧种子软删（若仍存在）
UPDATE t_retrieval_strategy
SET deleted = 1, delete_time = CURRENT_TIMESTAMP(3), is_default = 0
WHERE id IN ('rs_default', 'rs_vector_only') AND deleted = 0;

-- 保证仅基线为默认
UPDATE t_retrieval_strategy SET is_default = 0 WHERE id <> 'rs_baseline_vector' AND deleted = 0;
UPDATE t_retrieval_strategy SET is_default = 1 WHERE id = 'rs_baseline_vector' AND deleted = 0;

-- 知识库 / 应用绑定到新默认策略
UPDATE t_knowledge_base
SET retrieval_strategy_id = 'rs_baseline_vector'
WHERE deleted = 0 AND (retrieval_strategy_id IS NULL OR retrieval_strategy_id IN ('', 'rs_default', 'rs_vector_only'));

UPDATE t_qa_app
SET retrieval_strategy_id = 'rs_baseline_vector'
WHERE deleted = 0 AND retrieval_strategy_id IN ('rs_default', 'rs_vector_only');
