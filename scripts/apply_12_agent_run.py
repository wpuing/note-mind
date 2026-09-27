"""Apply agent run schema changes from 12-agent-run.sql (idempotent)."""
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


def column_exists(cur, table: str, column: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(1) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND COLUMN_NAME = %s
        """,
        (table, column),
    )
    return cur.fetchone()[0] > 0


def ensure_column(cur, table: str, column: str, ddl: str) -> None:
    if column_exists(cur, table, column):
        print(f"skip {table}.{column}")
        return
    cur.execute(ddl)
    print(f"add  {table}.{column}")


def main() -> None:
    conn = connect()
    try:
        with conn.cursor() as cur:
            ensure_column(
                cur,
                "t_agent_run",
                "error_message",
                "ALTER TABLE t_agent_run ADD COLUMN error_message VARCHAR(1024) NULL COMMENT '失败原因' AFTER final_answer",
            )
            ensure_column(
                cur,
                "t_agent_run",
                "rewrite_rounds",
                "ALTER TABLE t_agent_run ADD COLUMN rewrite_rounds INT NOT NULL DEFAULT 0 COMMENT '改写重试次数' AFTER retrieval_rounds",
            )
            ensure_column(
                cur,
                "t_agent_run",
                "knowledge_base_id",
                "ALTER TABLE t_agent_run ADD COLUMN knowledge_base_id VARCHAR(32) NULL COMMENT '知识库' AFTER session_id",
            )
            ensure_column(
                cur,
                "t_agent_run",
                "run_type",
                "ALTER TABLE t_agent_run ADD COLUMN run_type VARCHAR(32) NOT NULL DEFAULT 'AGENTIC_QA' COMMENT '运行类型' AFTER knowledge_base_id",
            )
            ensure_column(
                cur,
                "t_agent_run",
                "sources_json",
                "ALTER TABLE t_agent_run ADD COLUMN sources_json JSON NULL COMMENT '引用来源' AFTER rewrite_rounds",
            )
            ensure_column(
                cur,
                "t_agent_run",
                "conclusion_label",
                "ALTER TABLE t_agent_run ADD COLUMN conclusion_label VARCHAR(64) NULL COMMENT '正常回答/未找到等' AFTER sources_json",
            )
        print("done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
