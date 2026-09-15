"""Apply chat-log columns (idempotent)."""
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
                "t_chat_session",
                "client_source",
                "ALTER TABLE t_chat_session ADD COLUMN client_source VARCHAR(32) NOT NULL DEFAULT 'ADMIN' "
                "COMMENT 'ADMIN/USER' AFTER title",
            )
            ensure(
                cur,
                "t_chat_message",
                "settled",
                "ALTER TABLE t_chat_message ADD COLUMN settled TINYINT NOT NULL DEFAULT 0 "
                "COMMENT '反馈是否已沉淀' AFTER feedback_reason",
            )
        print("done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
