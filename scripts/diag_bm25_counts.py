#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import psycopg
import pymysql

ROOT = Path(__file__).resolve().parents[1]


def load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def main() -> None:
    env = load_env()
    mc = pymysql.connect(
        host="127.0.0.1",
        user=env.get("DB_USERNAME", "root"),
        password=env.get("DB_PASSWORD", "root"),
        database=env.get("MYSQL_DATABASE", "notemind"),
        charset="utf8mb4",
    )
    cur = mc.cursor()
    cur.execute(
        "SELECT knowledge_base_id, COUNT(1) FROM t_knowledge_segment WHERE deleted=0 GROUP BY knowledge_base_id"
    )
    print("mysql segs", cur.fetchall())
    cur.execute(
        "SELECT id, knowledge_base_id FROM t_knowledge_document WHERE deleted=0 LIMIT 10"
    )
    print("docs", cur.fetchall())
    mc.close()

    host = env.get("PG_HOST", "127.0.0.1")
    port = env.get("PG_PORT", "5432")
    db = env.get("PG_DATABASE", "notemind_vector")
    user = env.get("PG_USERNAME", "postgres")
    pwd = env.get("PG_PASSWORD", "123456")
    conn = psycopg.connect(f"host={host} port={port} dbname={db} user={user} password={pwd}")
    with conn.cursor() as c:
        c.execute(
            "SELECT knowledge_base_id, COUNT(1) FROM knowledge_segment_bm25 GROUP BY knowledge_base_id"
        )
        print("bm25 by kb", c.fetchall())
        c.execute(
            "SELECT knowledge_base_id, COUNT(1) FROM knowledge_segment_vector GROUP BY knowledge_base_id"
        )
        print("vector by kb", c.fetchall())
        c.execute(
            "SELECT segment_id, left(content, 40), knowledge_base_id FROM knowledge_segment_bm25 LIMIT 5"
        )
        print("bm25 sample", c.fetchall())
        # try plainto_tsquery on sample chinese
        q = "有偿维修"
        c.execute(
            """
            SELECT segment_id, ts_rank_cd(content_tsv, plainto_tsquery('simple', %s)) AS score
            FROM knowledge_segment_bm25
            WHERE content_tsv @@ plainto_tsquery('simple', %s)
            ORDER BY score DESC LIMIT 5
            """,
            (q, q),
        )
        print("tsv hits for", q, c.fetchall())
        c.execute(
            """
            SELECT segment_id FROM knowledge_segment_bm25
            WHERE content ILIKE %s LIMIT 5
            """,
            (f"%{q}%",),
        )
        print("ilike hits", c.fetchall())
    conn.close()


if __name__ == "__main__":
    main()
