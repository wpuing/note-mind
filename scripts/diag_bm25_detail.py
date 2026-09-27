#!/usr/bin/env python3
from __future__ import annotations

import json
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
    out = ROOT / "scripts" / "_diag_out.txt"
    lines: list[str] = []

    mc = pymysql.connect(
        host="127.0.0.1",
        user=env.get("DB_USERNAME", "root"),
        password=env.get("DB_PASSWORD", "root"),
        database=env.get("MYSQL_DATABASE", "notemind"),
        charset="utf8mb4",
    )
    cur = mc.cursor(pymysql.cursors.DictCursor)
    cur.execute(
        """
        SELECT id, question, expected_answer, source_type, include_in_eval
        FROM t_eval_case WHERE deleted=0 AND dataset_id='ed_4774d425b86443'
        ORDER BY create_time
        """
    )
    cases = cur.fetchall()
    lines.append("=== eval cases ===")
    for c in cases:
        lines.append(
            f"{c['id']} include={c['include_in_eval']} type={c['source_type']} q={c['question']!r} a={c['expected_answer']!r}"
        )
    mc.close()

    host = env.get("PG_HOST", "127.0.0.1")
    port = env.get("PG_PORT", "5432")
    db = env.get("PG_DATABASE", "notemind_vector")
    user = env.get("PG_USERNAME", "postgres")
    pwd = env.get("PG_PASSWORD", "123456")
    kb = "kb_a67e54d3188247ea"
    conn = psycopg.connect(f"host={host} port={port} dbname={db} user={user} password={pwd}")
    with conn.cursor() as c:
        c.execute(
            "SELECT segment_id, left(content, 120) FROM knowledge_segment_bm25 WHERE knowledge_base_id=%s",
            (kb,),
        )
        rows = c.fetchall()
        lines.append(f"=== bm25 content for {kb} count={len(rows)} ===")
        for sid, content in rows:
            lines.append(f"{sid}: {content!r}")

        for cased in cases:
            q = (cased["question"] or "").strip()
            if not q:
                continue
            c.execute(
                """
                SELECT COUNT(1) FROM knowledge_segment_bm25
                WHERE knowledge_base_id=%s AND content_tsv @@ plainto_tsquery('simple', %s)
                """,
                (kb, q),
            )
            tsv_n = c.fetchone()[0]
            # try first 8 chars for ilike
            frag = q[:8] if len(q) >= 2 else q
            c.execute(
                """
                SELECT COUNT(1) FROM knowledge_segment_bm25
                WHERE knowledge_base_id=%s AND content ILIKE %s
                """,
                (kb, f"%{frag}%"),
            )
            like_n = c.fetchone()[0]
            lines.append(f"q={q[:40]!r} tsv_hits={tsv_n} ilike({frag!r})={like_n}")

            # jieba-ish: extract chinese chars length >=2 substrings? just check token issue
            c.execute("SELECT plainto_tsquery('simple', %s)::text", (q,))
            lines.append(f"  plainto_tsquery={c.fetchone()[0]!r}")
            c.execute(
                "SELECT left(content_tsv::text, 200) FROM knowledge_segment_bm25 WHERE knowledge_base_id=%s LIMIT 1",
                (kb,),
            )
            tsv = c.fetchone()
            sample = repr(tsv[0]) if tsv else "None"
            lines.append(f"  content_tsv sample={sample}")

    conn.close()
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
