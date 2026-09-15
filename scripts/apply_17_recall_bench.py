#!/usr/bin/env python3
"""Apply recall-bench schema (idempotent)."""
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


def main() -> None:
    conn = pymysql.connect(
        host=env.get("DB_HOST", "127.0.0.1"),
        port=int(env.get("DB_PORT", "3306")),
        user=env.get("DB_USERNAME", "root"),
        password=env.get("DB_PASSWORD", "root"),
        database=env.get("MYSQL_DATABASE") or env.get("DB_NAME", "notemind"),
        autocommit=True,
        charset="utf8mb4",
    )
    sql = (ROOT / "scripts" / "db" / "17-recall-bench.sql").read_text(encoding="utf-8")
    # strip USE
    stmts = [s.strip() for s in sql.split(";") if s.strip() and not s.strip().upper().startswith("USE ")]
    with conn.cursor() as cur:
        for st in stmts:
            cur.execute(st)
            print("ok", st.split("(")[0][:60].replace("\n", " "))
    conn.close()
    print("done")


if __name__ == "__main__":
    main()
