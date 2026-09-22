"""Apply retrieval strategy schema changes (idempotent)."""
from __future__ import annotations

from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
env: dict[str, str] = {}
for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    env[k.strip()] = v.strip().strip('"').strip("'")


def connect():
    return pymysql.connect(
        host=env.get("DB_HOST", "127.0.0.1"),
        port=int(env.get("DB_PORT", "3306")),
        user=env.get("DB_USERNAME", "root"),
        password=env.get("DB_PASSWORD", "root"),
        database=env.get("DB_NAME", "notemind"),
        autocommit=True,
        charset="utf8mb4",
        connect_timeout=10,
        read_timeout=60,
        write_timeout=60,
    )


def column_exists(cur, table: str, column: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(1) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND COLUMN_NAME = %s
        """,
        (table, column),
    )
    return int(cur.fetchone()[0]) > 0


def index_exists(cur, table: str, index: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(1) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND INDEX_NAME = %s
        """,
        (table, index),
    )
    return int(cur.fetchone()[0]) > 0


conn = connect()
cur = conn.cursor()

alters = [
    (
        "t_retrieval_strategy",
        "is_default",
        "ALTER TABLE t_retrieval_strategy ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1=系统默认策略' AFTER enabled",
    ),
    (
        "t_retrieval_strategy",
        "rewrite_count",
        "ALTER TABLE t_retrieval_strategy ADD COLUMN rewrite_count INT NOT NULL DEFAULT 3 COMMENT '多查询扩展条数' AFTER rewrite_mode",
    ),
    (
        "t_retrieval_strategy",
        "rerank_top_n",
        "ALTER TABLE t_retrieval_strategy ADD COLUMN rerank_top_n INT NOT NULL DEFAULT 4 COMMENT '重排保留条数' AFTER top_k",
    ),
    (
        "t_knowledge_base",
        "retrieval_strategy_id",
        "ALTER TABLE t_knowledge_base ADD COLUMN retrieval_strategy_id VARCHAR(32) NULL COMMENT '检索策略' AFTER chunk_strategy_id",
    ),
]

for table, col, ddl in alters:
    if column_exists(cur, table, col):
        print("skip col", table, col)
    else:
        cur.execute(ddl)
        print("added col", table, col)

if not index_exists(cur, "t_knowledge_base", "idx_kb_retrieval"):
    cur.execute("ALTER TABLE t_knowledge_base ADD KEY idx_kb_retrieval (retrieval_strategy_id)")
    print("added index idx_kb_retrieval")
else:
    print("skip index idx_kb_retrieval")

cur.execute(
    """
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
  remark = VALUES(remark),
  deleted = 0,
  delete_time = NULL
"""
)
print("upsert strategies ok")

cur.execute(
    """
UPDATE t_retrieval_strategy
SET deleted = 1, delete_time = CURRENT_TIMESTAMP(3), is_default = 0
WHERE id IN ('rs_default', 'rs_vector_only') AND deleted = 0
"""
)
cur.execute(
    "UPDATE t_retrieval_strategy SET is_default = 0 WHERE id <> 'rs_baseline_vector' AND deleted = 0"
)
cur.execute(
    "UPDATE t_retrieval_strategy SET is_default = 1 WHERE id = 'rs_baseline_vector' AND deleted = 0"
)
cur.execute(
    """
UPDATE t_knowledge_base
SET retrieval_strategy_id = 'rs_baseline_vector'
WHERE deleted = 0 AND (
  retrieval_strategy_id IS NULL
  OR retrieval_strategy_id IN ('', 'rs_default', 'rs_vector_only')
)
"""
)
cur.execute(
    """
UPDATE t_qa_app
SET retrieval_strategy_id = 'rs_baseline_vector'
WHERE deleted = 0 AND retrieval_strategy_id IN ('rs_default', 'rs_vector_only')
"""
)

cur.execute(
    "SELECT id, name, is_default, enable_vector, enable_bm25, enable_rerank FROM t_retrieval_strategy WHERE deleted = 0 ORDER BY is_default DESC, name"
)
print("strategies:", cur.fetchall())
cur.execute(
    "SELECT id, retrieval_strategy_id FROM t_knowledge_base WHERE deleted = 0"
)
print("kbs:", cur.fetchall())
conn.close()
print("done")
