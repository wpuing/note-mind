#!/usr/bin/env python3
"""Diagnose why baseline vs hybrid eval scores look identical."""
from __future__ import annotations

import json
import urllib.request
from pathlib import Path

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
    conn = pymysql.connect(
        host=env.get("DB_HOST", "127.0.0.1"),
        port=int(env.get("DB_PORT", "3306")),
        user=env.get("DB_USERNAME", "root"),
        password=env.get("DB_PASSWORD", "root"),
        database=env.get("MYSQL_DATABASE", "notemind"),
        charset="utf8mb4",
    )
    cur = conn.cursor(pymysql.cursors.DictCursor)
    cur.execute(
        """
        SELECT id, dataset_id, strategy_name, retrieval_strategy_id,
               context_recall, context_precision, faithfulness, answer_relevancy,
               overall_score, detail_json
        FROM t_eval_report
        WHERE deleted = 0
        ORDER BY create_time DESC
        LIMIT 5
        """
    )
    reports = cur.fetchall()
    print("=== reports ===")
    for r in reports:
        print(
            r["id"],
            r["strategy_name"],
            r["retrieval_strategy_id"],
            "overall=",
            r["overall_score"],
            "R/P/F/A=",
            r["context_recall"],
            r["context_precision"],
            r["faithfulness"],
            r["answer_relevancy"],
        )
        detail = r.get("detail_json")
        if not detail:
            continue
        d = json.loads(detail) if isinstance(detail, str) else detail
        cases = d.get("cases") or []
        print("  cases=", len(cases))
        for i, c0 in enumerate(cases[:2]):
            print(
                f"  [{i}] scores",
                c0.get("contextRecall"),
                c0.get("contextPrecision"),
                c0.get("faithfulness"),
                c0.get("answerRelevancy"),
                "ans=",
                (c0.get("generatedAnswer") or "")[:40].replace("\n", " "),
            )

    if not reports:
        print("no reports")
        return

    ds_id = reports[0]["dataset_id"]
    cur.execute(
        "SELECT knowledge_base_id FROM t_eval_dataset WHERE id=%s",
        (ds_id,),
    )
    kb = cur.fetchone()["knowledge_base_id"]
    cur.execute(
        """
        SELECT question, expected_answer, source_segment_ids
        FROM t_eval_case
        WHERE deleted=0 AND dataset_id=%s AND include_in_eval=1
        ORDER BY create_time ASC
        LIMIT 3
        """,
        (ds_id,),
    )
    cases = cur.fetchall()
    print("=== cases ===")
    for c in cases:
        print("-", c["question"][:60], "| exp=", (c["expected_answer"] or "")[:30], "| segs=", c["source_segment_ids"])

    cur.execute(
        """
        SELECT id, name, enable_vector, enable_bm25, enable_rrf, top_k, vector_top_k, bm25_top_k
        FROM t_retrieval_strategy WHERE deleted=0
        """
    )
    print("=== strategies ===")
    for s in cur.fetchall():
        print(s)
    conn.close()

    token = env.get("AI_ENGINE_TOKEN", "local-dev-ai-engine-token")
    q = cases[0]["question"]

    def call(name: str, **kwargs):
        body = {
            "question": q,
            "knowledge_base_id": kb,
            "enable_rerank": False,
            "enable_rewrite": False,
            "enable_parent_fill": False,
            "rrf_k": 60,
            "strategy_name": name,
            **kwargs,
        }
        req = urllib.request.Request(
            "http://127.0.0.1:8000/api/v1/ai/retrieval/test",
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "X-AI-Engine-Token": token,
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        srcs = data.get("sources") or []
        ids = [s.get("segment_id") for s in srcs]
        stages = [(st.get("key"), st.get("count_text"), st.get("name")) for st in (data.get("stages") or [])]
        print(f"--- {name} hits={len(srcs)}")
        print("  stages=", stages)
        print("  ids=", ids)
        for i, s in enumerate(srcs[:5]):
            preview = (s.get("content") or "")[:48].replace("\n", " ")
            print(f"  [{i}] score={s.get('score')} ch={s.get('recall_source')} {preview}")
        return ids

    print("=== live retrieval ===")
    print("question=", q)
    base_ids = call(
        "baseline",
        enable_vector=True,
        enable_bm25=False,
        enable_rrf=False,
        top_k=5,
        vector_top_k=5,
        bm25_top_k=0,
    )
    hyb_ids = call(
        "hybrid",
        enable_vector=True,
        enable_bm25=True,
        enable_rrf=True,
        top_k=8,
        vector_top_k=8,
        bm25_top_k=8,
    )
    print("same_top?", base_ids == hyb_ids)
    print("overlap=", len(set(base_ids) & set(hyb_ids)), "/", len(set(base_ids) | set(hyb_ids)))


if __name__ == "__main__":
    main()
