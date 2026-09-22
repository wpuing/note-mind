"""PostgreSQL 连接池（psycopg_pool）。"""
from __future__ import annotations

from contextlib import contextmanager
from typing import Iterator

import psycopg
from pgvector.psycopg import register_vector
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.core.config import settings

_pool: ConnectionPool | None = None


def _dsn() -> str:
    return (
        f"host={settings.pg_host} port={settings.pg_port} "
        f"dbname={settings.pg_database} user={settings.pg_user} "
        f"password={settings.pg_password}"
    )


def _configure(conn: psycopg.Connection) -> None:
    conn.row_factory = dict_row
    register_vector(conn)


def get_pool() -> ConnectionPool:
    global _pool
    if _pool is None:
        _pool = ConnectionPool(
            conninfo=_dsn(),
            min_size=1,
            max_size=8,
            kwargs={"row_factory": dict_row},
            configure=_configure,
            open=True,
        )
    return _pool


@contextmanager
def pg_conn() -> Iterator[psycopg.Connection]:
    """从连接池借连接；成功 commit，异常 rollback。"""
    pool = get_pool()
    with pool.connection() as conn:
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None
