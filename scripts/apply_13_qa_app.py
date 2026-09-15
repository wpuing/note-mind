"""Apply qa-app schema + demo seeds (idempotent)."""
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


def pick(cur, sql: str, args=(), fallback: str = "") -> str:
    cur.execute(sql, args)
    row = cur.fetchone()
    return row[0] if row and row[0] else fallback


def seed_if_missing(cur, app_id: str, sql: str, args: tuple) -> None:
    cur.execute("SELECT COUNT(1) FROM t_qa_app WHERE id=%s", (app_id,))
    if cur.fetchone()[0] > 0:
        print(f"skip seed {app_id}")
        return
    cur.execute(sql, args)
    print(f"seed {app_id}")


def main() -> None:
    conn = connect()
    try:
        with conn.cursor() as cur:
            ensure_column(
                cur,
                "t_qa_app",
                "history_limit",
                "ALTER TABLE t_qa_app ADD COLUMN history_limit INT NOT NULL DEFAULT 6 "
                "COMMENT '携带历史消息条数' AFTER enable_agentic",
            )
            ensure_column(
                cur,
                "t_qa_app",
                "fallback_reply",
                "ALTER TABLE t_qa_app ADD COLUMN fallback_reply VARCHAR(512) NULL "
                "COMMENT '未命中时兜底话术' AFTER history_limit",
            )

            kb_after = pick(
                cur,
                "SELECT id FROM t_knowledge_base WHERE deleted=0 AND name LIKE %s LIMIT 1",
                ("%售后%",),
                "kb_default",
            )
            kb_policy = pick(
                cur,
                "SELECT id FROM t_knowledge_base WHERE deleted=0 AND name LIKE %s LIMIT 1",
                ("%制度%",),
                "kb_default",
            )
            kb_any = pick(
                cur,
                "SELECT id FROM t_knowledge_base WHERE deleted=0 ORDER BY create_time DESC LIMIT 1",
                (),
                "kb_default",
            )
            rs_hybrid = pick(
                cur,
                "SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND name LIKE %s LIMIT 1",
                ("%进阶%",),
                "rs_hybrid",
            )
            rs_default = pick(
                cur,
                "SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND is_default=1 LIMIT 1",
                (),
                "rs_baseline_vector",
            )
            rs_full = pick(
                cur,
                "SELECT id FROM t_retrieval_strategy WHERE deleted=0 AND name LIKE %s LIMIT 1",
                ("%完整%",),
                rs_default,
            )

            insert_sql = """
                INSERT INTO t_qa_app (
                  id, create_time, update_time, deleted,
                  name, description, knowledge_base_id, retrieval_strategy_id,
                  chat_model_id, answer_prompt_id, enable_agentic, history_limit, fallback_reply, enabled
                ) VALUES (%s, NOW(3), NOW(3), 0, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1)
            """
            seed_if_missing(
                cur,
                "app_after_sales",
                insert_sql,
                (
                    "app_after_sales",
                    "售后智能客服",
                    "面向客服坐席和客户，回答质保、维修、备件与退换货问题，走 Agentic 链路自行判断要不要重新检索。",
                    kb_after,
                    rs_hybrid,
                    "m_chat_qwen",
                    "p_rag_answer",
                    1,
                    6,
                    "这个问题在售后知识库里没有查到，请转接人工客服 400-820-9600。",
                ),
            )
            seed_if_missing(
                cur,
                "app_policy",
                insert_sql,
                (
                    "app_policy",
                    "企业制度问答",
                    "面向员工的制度与流程问答，直线链路检索生成。",
                    kb_policy,
                    rs_default,
                    "m_chat_qwen",
                    "p_rag_answer",
                    0,
                    8,
                    "制度库中暂未找到相关条款，请联系行政同事确认。",
                ),
            )
            seed_if_missing(
                cur,
                "app_product",
                insert_sql,
                (
                    "app_product",
                    "产品技术助手",
                    "产品参数与技术问题助手，Agentic 模式。",
                    kb_any,
                    rs_full,
                    "m_chat_qwen",
                    "p_rag_answer",
                    1,
                    6,
                    "产品资料中暂无相关说明，请联系技术支持。",
                ),
            )
        print("done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
