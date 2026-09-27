"""Apply eval dataset schema (idempotent)."""
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
        database=env.get("MYSQL_DATABASE") or env.get("DB_NAME", "notemind"),
        autocommit=True,
        charset="utf8mb4",
        connect_timeout=10,
    )


def ensure(cur, table: str, column: str, ddl: str) -> None:
    cur.execute(
        """
        SELECT COUNT(1) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND COLUMN_NAME=%s
        """,
        (table, column),
    )
    if cur.fetchone()[0] > 0:
        print(f"skip {table}.{column}")
        return
    cur.execute(ddl)
    print(f"add  {table}.{column}")


def main() -> None:
    conn = connect()
    try:
        with conn.cursor() as cur:
            ensure(
                cur,
                "t_eval_dataset",
                "knowledge_base_id",
                "ALTER TABLE t_eval_dataset ADD COLUMN knowledge_base_id VARCHAR(32) NULL "
                "COMMENT '关联知识库' AFTER description",
            )
            ensure(
                cur,
                "t_eval_case",
                "source_type",
                "ALTER TABLE t_eval_case ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' "
                "COMMENT 'MANUAL/FROM_DOC/FROM_DISLIKE' AFTER document_id",
            )
            ensure(
                cur,
                "t_eval_case",
                "include_in_eval",
                "ALTER TABLE t_eval_case ADD COLUMN include_in_eval TINYINT NOT NULL DEFAULT 1 "
                "COMMENT '是否参与评测' AFTER source_type",
            )
            ensure(
                cur,
                "t_eval_case",
                "remark",
                "ALTER TABLE t_eval_case ADD COLUMN remark VARCHAR(512) NULL "
                "COMMENT '备注' AFTER include_in_eval",
            )
            ensure(
                cur,
                "t_eval_case",
                "source_content",
                "ALTER TABLE t_eval_case ADD COLUMN source_content MEDIUMTEXT NULL "
                "COMMENT '来源片段原文快照' AFTER remark",
            )
            ensure(
                cur,
                "t_eval_case",
                "source_label",
                "ALTER TABLE t_eval_case ADD COLUMN source_label VARCHAR(256) NULL "
                "COMMENT '片段定位说明' AFTER source_content",
            )
        print("done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
