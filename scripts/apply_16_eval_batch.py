"""Apply eval batch schema + judge prompts (idempotent)."""
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


def ensure_index(cur, table: str, index: str, ddl: str) -> None:
    cur.execute(
        """
        SELECT COUNT(1) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND INDEX_NAME=%s
        """,
        (table, index),
    )
    if cur.fetchone()[0] > 0:
        print(f"skip index {index}")
        return
    cur.execute(ddl)
    print(f"add  index {index}")


def main() -> None:
    conn = connect()
    try:
        with conn.cursor() as cur:
            ensure(
                cur,
                "t_eval_report",
                "task_no",
                "ALTER TABLE t_eval_report ADD COLUMN task_no BIGINT NULL COMMENT '展示用任务号' AFTER id",
            )
            ensure(
                cur,
                "t_eval_report",
                "done_count",
                "ALTER TABLE t_eval_report ADD COLUMN done_count INT NOT NULL DEFAULT 0 COMMENT '已完成用例数' AFTER case_count",
            )
            ensure(
                cur,
                "t_eval_report",
                "fail_reason",
                "ALTER TABLE t_eval_report ADD COLUMN fail_reason VARCHAR(1024) NULL COMMENT '任务失败原因' AFTER overall_score",
            )
            ensure(
                cur,
                "t_eval_report",
                "duration_ms",
                "ALTER TABLE t_eval_report ADD COLUMN duration_ms BIGINT NULL COMMENT '总耗时毫秒' AFTER fail_reason",
            )
            ensure(
                cur,
                "t_eval_report",
                "dataset_name",
                "ALTER TABLE t_eval_report ADD COLUMN dataset_name VARCHAR(128) NULL COMMENT '评测集名称快照' AFTER dataset_id",
            )
            ensure(
                cur,
                "t_eval_report",
                "strategy_name",
                "ALTER TABLE t_eval_report ADD COLUMN strategy_name VARCHAR(128) NULL COMMENT '检索策略名称快照' AFTER retrieval_strategy_id",
            )
            cur.execute(
                """
                UPDATE t_eval_report
                SET task_no = CONV(RIGHT(REPLACE(id, '-', ''), 8), 16, 10) % 900000 + 100000
                WHERE task_no IS NULL
                """
            )
            ensure_index(
                cur,
                "t_eval_report",
                "uk_eval_report_task_no",
                "ALTER TABLE t_eval_report ADD UNIQUE KEY uk_eval_report_task_no (task_no)",
            )

            prompts = [
                (
                    "p_eval_ctx_recall",
                    "EVAL_CONTEXT_RECALL",
                    "上下文召回评测",
                    "效果评测",
                    "你是 RAG 评测裁判（temperature=0）。判断：标准答案中的关键信息，是否能从检索资料中找到依据。\n问题：{question}\n标准答案：{expected}\n检索资料：\n{context}\n\n只输出 JSON：{\"score\":0或1,\"reason\":\"一句理由\"}。score=1 表示标准答案要点已被资料覆盖。",
                    '["question","expected","context"]',
                    "Context Recall Judge",
                ),
                (
                    "p_eval_ctx_precision",
                    "EVAL_CONTEXT_PRECISION",
                    "上下文精度评测",
                    "效果评测",
                    "你是 RAG 评测裁判（temperature=0）。对每条检索资料判断是否对回答问题/对齐标准答案有用。\n问题：{question}\n标准答案：{expected}\n资料列表（按排名）：\n{context}\n\n只输出 JSON：{\"scores\":[0或1,...],\"reason\":\"一句理由\"}。scores 与资料条数一一对应，1=有用。",
                    '["question","expected","context"]',
                    "Context Precision Judge",
                ),
                (
                    "p_eval_faithfulness",
                    "EVAL_FAITHFULNESS",
                    "忠实度评测",
                    "效果评测",
                    "你是 RAG 评测裁判（temperature=0）。判断生成答案是否忠实于资料（有无编造）。\n问题：{question}\n资料：\n{context}\n\n生成答案：\n{answer}\n\n只输出 JSON：{\"score\":0到1的小数,\"reason\":\"一句理由\"}。1=完全忠实，0=严重幻觉。",
                    '["question","context","answer"]',
                    "Faithfulness Judge",
                ),
                (
                    "p_eval_relevancy",
                    "EVAL_ANSWER_RELEVANCY",
                    "答案相关性评测",
                    "效果评测",
                    "你是 RAG 评测裁判（temperature=0）。判断生成答案是否在回答该问题（有无跑题）。\n问题：{question}\n生成答案：\n{answer}\n\n只输出 JSON：{\"score\":0到1的小数,\"reason\":\"一句理由\"}。",
                    '["question","answer"]',
                    "Answer Relevancy Judge",
                ),
                (
                    "p_eval_rel_questions",
                    "EVAL_RELEVANCY_QUESTIONS",
                    "答案反推问题",
                    "效果评测",
                    "根据下面的答案，反推出 3 个用户可能提出的问题。每行一个问题，不要编号。\n答案：\n{answer}",
                    '["answer"]',
                    "Answer Relevancy 辅助：从答案反推问题",
                ),
            ]
            for pid, code, name, scenario, content, variables, remark in prompts:
                cur.execute(
                    """
                    INSERT INTO t_prompt_template (
                      id, code, name, scenario, content, variables_json, enabled, remark
                    ) VALUES (%s, %s, %s, %s, %s, CAST(%s AS JSON), 1, %s)
                    ON DUPLICATE KEY UPDATE
                      code=VALUES(code), name=VALUES(name), scenario=VALUES(scenario),
                      content=VALUES(content), variables_json=VALUES(variables_json),
                      enabled=1, remark=VALUES(remark), deleted=0, delete_time=NULL
                    """,
                    (pid, code, name, scenario, content, variables, remark),
                )
                print(f"prompt {code}")
        print("done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
