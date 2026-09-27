# -*- coding: utf-8 -*-
"""NoteMind 全流程 API 冒烟（Admin + Web + AI Engine）。

前提：Gateway:8080 / Service:8081 / AI:8000 / MySQL / PG / Redis 已启动。
用法：
  python scripts/e2e_full_flow.py
"""
from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path
from typing import Any

import httpx

GW = "http://127.0.0.1:8080"
AI = "http://127.0.0.1:8000"
AI_TOKEN = "local-dev-ai-engine-token"

PASS = 0
FAIL = 0
SKIP = 0
RESULTS: list[tuple[str, str, str]] = []


def ok(name: str, detail: str = "") -> None:
    global PASS
    PASS += 1
    RESULTS.append(("PASS", name, detail))
    print(f"  [PASS] {name}" + (f" — {detail}" if detail else ""))


def fail(name: str, detail: str = "") -> None:
    global FAIL
    FAIL += 1
    RESULTS.append(("FAIL", name, detail))
    print(f"  [FAIL] {name} — {detail}")


def skip(name: str, detail: str = "") -> None:
    global SKIP
    SKIP += 1
    RESULTS.append(("SKIP", name, detail))
    print(f"  [SKIP] {name} — {detail}")


def unwrap(data: Any) -> Any:
    if isinstance(data, dict) and "code" in data:
        if data.get("code") != 0:
            raise AssertionError(f"biz code={data.get('code')} msg={data.get('message')}")
        return data.get("data")
    return data


class Client:
    def __init__(self) -> None:
        self.c = httpx.Client(timeout=120.0)
        self.token: str | None = None

    def close(self) -> None:
        self.c.close()

    def headers(self) -> dict[str, str]:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def login(self, username: str, password: str) -> dict[str, Any]:
        r = self.c.post(
            f"{GW}/api/v1/auth/login",
            headers={"Content-Type": "application/json"},
            json={"username": username, "password": password},
        )
        r.raise_for_status()
        data = unwrap(r.json())
        self.token = data["accessToken"]
        return data

    def get(self, path: str, **kw: Any) -> Any:
        r = self.c.get(f"{GW}{path}", headers=self.headers(), **kw)
        if r.status_code >= 400:
            raise AssertionError(f"GET {path} -> {r.status_code} {r.text[:300]}")
        return unwrap(r.json())

    def post(self, path: str, body: Any = None, **kw: Any) -> Any:
        r = self.c.post(f"{GW}{path}", headers=self.headers(), json=body, **kw)
        if r.status_code >= 400:
            raise AssertionError(f"POST {path} -> {r.status_code} {r.text[:400]}")
        if not r.content:
            return None
        return unwrap(r.json())

    def put(self, path: str, body: Any = None) -> Any:
        r = self.c.put(f"{GW}{path}", headers=self.headers(), json=body)
        if r.status_code >= 400:
            raise AssertionError(f"PUT {path} -> {r.status_code} {r.text[:300]}")
        return unwrap(r.json())

    def delete(self, path: str) -> Any:
        r = self.c.delete(f"{GW}{path}", headers=self.headers())
        if r.status_code >= 400:
            raise AssertionError(f"DELETE {path} -> {r.status_code} {r.text[:300]}")
        if not r.content:
            return None
        return unwrap(r.json())


def section(title: str) -> None:
    print(f"\n=== {title} ===")


def run_admin(cli: Client) -> dict[str, Any]:
    ctx: dict[str, Any] = {}
    section("Admin 登录 / 资料 / 工作台")
    try:
        data = cli.login("admin", "changeme")
        assert data.get("accessToken")
        ok("admin.login", f"role={data.get('user', {}).get('role')}")
    except Exception as e:
        fail("admin.login", str(e))
        return ctx

    for name, path in [
        ("admin.me", "/api/v1/auth/me"),
        ("admin.dashboard", "/api/v1/dashboard/overview?days=7"),
        ("admin.ping", "/api/v1/ping"),
    ]:
        try:
            cli.get(path)
            ok(name)
        except Exception as e:
            fail(name, str(e))

    section("知识库 DDD CRUD")
    kb_id = None
    try:
        suffix = uuid.uuid4().hex[:8]
        created = cli.post(
            "/api/v1/knowledge/bases",
            {
                "name": f"e2e-kb-{suffix}",
                "category": "e2e",
                "description": "automated e2e",
                "status": 1,
            },
        )
        kb_id = created["id"]
        ctx["kb_id"] = kb_id
        ok("kb.create", kb_id)
        page = cli.get("/api/v1/knowledge/bases/page?page=1&pageSize=8&name=e2e-kb")
        assert page.get("total", 0) >= 1
        ok("kb.page", f"total={page.get('total')}")
        detail = cli.get(f"/api/v1/knowledge/bases/{kb_id}")
        assert detail["id"] == kb_id
        ok("kb.detail", f"vectorStatus={detail.get('vectorStatus')}")
        docs = cli.get(f"/api/v1/knowledge/bases/{kb_id}/vector-documents")
        ok("kb.vector_documents", f"n={len(docs or [])}")
        updated = cli.put(
            f"/api/v1/knowledge/bases/{kb_id}",
            {
                "name": f"e2e-kb-{suffix}-u",
                "category": "e2e",
                "description": "updated",
                "status": 1,
            },
        )
        assert "updated" in (updated.get("description") or "") or updated.get("name", "").endswith("-u")
        ok("kb.update")
        enabled = cli.get("/api/v1/knowledge/bases")
        ok("kb.list_enabled", f"n={len(enabled or [])}")
    except Exception as e:
        fail("kb.crud", str(e))

    section("Prompt DDD CRUD")
    prompt_id = None
    try:
        suffix = uuid.uuid4().hex[:6].upper()
        code = f"E2E_PROMPT_{suffix}"
        created = cli.post(
            "/api/v1/prompt-templates",
            {
                "code": code,
                "name": f"e2e-prompt-{suffix}",
                "scenario": "问答生成",
                "content": "你好 {user}，问题：{question}",
                "enabled": 1,
                "remark": "e2e",
            },
        )
        prompt_id = created["id"]
        ctx["prompt_id"] = prompt_id
        assert "user" in (created.get("variables") or "")
        ok("prompt.create", f"vars={created.get('variables')}")
        page = cli.get("/api/v1/prompt-templates/page?page=1&pageSize=8&name=e2e-prompt")
        assert page.get("total", 0) >= 1
        ok("prompt.page")
        cli.put(
            f"/api/v1/prompt-templates/{prompt_id}",
            {
                "code": code,
                "name": f"e2e-prompt-{suffix}-u",
                "scenario": "问答生成",
                "content": "更新 {user} {{context}}",
                "enabled": 1,
            },
        )
        ok("prompt.update")
        cli.delete(f"/api/v1/prompt-templates/{prompt_id}")
        ok("prompt.delete")
        ctx.pop("prompt_id", None)
    except Exception as e:
        fail("prompt.crud", str(e))

    section("配置 / 模型 / 工具 / 策略列表")
    for name, path in [
        ("categories", "/api/v1/knowledge/categories"),
        ("chunk_strategies", "/api/v1/knowledge/chunk-strategies"),
        ("retrieval_strategies", "/api/v1/knowledge/retrieval-strategies"),
        ("ai_models_page", "/api/v1/ai-models/page?page=1&pageSize=8"),
        ("ai_models", "/api/v1/ai-models"),
        ("agent_tools", "/api/v1/agent-tools/page?page=1&pageSize=8"),
        ("agent_tool_logs", "/api/v1/agent-tool-call-logs/page?page=1&pageSize=8"),
        ("agent_runs", "/api/v1/agent-runs/page?page=1&pageSize=8"),
        ("qa_apps_page", "/api/v1/qa-apps/page?page=1&pageSize=8"),
        ("chat_sessions_log", "/api/v1/chat-logs/sessions/page?page=1&pageSize=8"),
        ("chat_feedbacks", "/api/v1/chat-logs/feedback/page?page=1&pageSize=8"),
        ("chat_stats", "/api/v1/chat-logs/stats"),
        ("eval_datasets", "/api/v1/eval/datasets"),
        ("eval_reports", "/api/v1/eval/reports/page?page=1&pageSize=8"),
        ("recall_bench", "/api/v1/knowledge/recall-bench/page?page=1&pageSize=8"),
        ("documents", "/api/v1/knowledge/documents?page=1&pageSize=8"),
        ("segments", "/api/v1/knowledge/segments?page=1&pageSize=8"),
    ]:
        try:
            data = cli.get(path)
            extra = ""
            if isinstance(data, dict) and "total" in data:
                extra = f"total={data.get('total')}"
            elif isinstance(data, list):
                extra = f"n={len(data)}"
            ok(f"list.{name}", extra)
        except Exception as e:
            fail(f"list.{name}", str(e))

    section("文档上传 → 切分 → 向量化 → 检索")
    try:
        if not kb_id:
            skip("doc.pipeline", "no kb")
        else:
            sample = Path(__file__).resolve().parents[1] / "scripts" / "_e2e_sample.md"
            sample.write_text(
                "# E2E Sample\n\nNoteMind 自动化测试文档。\n\n关键能力：知识库、检索、问答。\n",
                encoding="utf-8",
            )
            with sample.open("rb") as f:
                files = {"file": ("e2e-sample.md", f, "text/markdown")}
                data = {"knowledgeBaseId": kb_id}
                # multipart: drop JSON content-type
                hdrs = {"Authorization": f"Bearer {cli.token}"}
                r = cli.c.post(
                    f"{GW}/api/v1/knowledge/documents/upload",
                    headers=hdrs,
                    files=files,
                    data=data,
                    timeout=180.0,
                )
            if r.status_code >= 400:
                raise AssertionError(f"upload {r.status_code} {r.text[:400]}")
            doc = unwrap(r.json())
            doc_id = doc["id"]
            ctx["doc_id"] = doc_id
            ok("doc.upload", doc_id)

            chunked = cli.post(f"/api/v1/knowledge/documents/{doc_id}/chunk", {})
            ok("doc.chunk", f"status={chunked.get('parseStatus')}")

            vec = cli.post(f"/api/v1/knowledge/documents/{doc_id}/vectorize", {})
            ok("doc.vectorize", f"status={vec.get('parseStatus')}")

            # wait briefly if async
            for _ in range(20):
                d = cli.get(f"/api/v1/knowledge/documents/{doc_id}")
                st = (d.get("parseStatus") or "").upper()
                if st in {"READY", "EMBEDDED", "DONE", "VECTORIZED"} or (
                    d.get("vectorStatus") or ""
                ).upper() in {"DONE", "READY"}:
                    break
                time.sleep(1.5)

            rs_page = cli.get("/api/v1/knowledge/retrieval-strategies/page?page=1&pageSize=5")
            records = (rs_page or {}).get("records") or []
            strategy_id = records[0]["id"] if records else None
            body = {
                "knowledgeBaseId": kb_id,
                "question": "NoteMind 关键能力有哪些",
            }
            if strategy_id:
                body["retrievalStrategyId"] = strategy_id
            if doc_id:
                body["documentIds"] = [doc_id]
            ret = cli.post("/api/v1/knowledge/retrieval/test", body)
            hits = ret.get("sources") or ret.get("segments") or ret.get("hits") or []
            hit_count = ret.get("hitCount")
            if hit_count is None:
                hit_count = len(hits)
            ok("retrieval.test", f"hits={hit_count}")
            if hit_count == 0:
                RESULTS.append(("WARN", "retrieval.hits", "0 hits after vectorize"))
                print("  [WARN] retrieval.hits — 0 after vectorize (non-blocking)")
            ctx["strategy_id"] = strategy_id
    except Exception as e:
        fail("doc.pipeline", str(e))

    section("应用 / Agent / 问答 SSE（Admin）")
    try:
        apps = cli.get("/api/v1/qa-apps/page?page=1&pageSize=20")
        records = (apps or {}).get("records") or []
        app = next((a for a in records if a.get("status") in (1, "1", "ENABLED", "enabled")), None)
        if not app and records:
            app = records[0]
        if not app:
            # create temporary app
            if kb_id:
                app = cli.post(
                    "/api/v1/qa-apps",
                    {
                        "name": f"e2e-app-{uuid.uuid4().hex[:6]}",
                        "knowledgeBaseId": kb_id,
                        "retrievalStrategyId": ctx.get("strategy_id"),
                        "enableAgentic": 0,
                        "enabled": 1,
                        "historyLimit": 5,
                        "fallbackReply": "暂无相关内容",
                    },
                )
                ctx["app_created"] = app["id"]
                ok("app.create", app["id"])
            else:
                skip("app", "no app and no kb")
                app = None
        else:
            ok("app.reuse", app.get("id"))

        if app:
            ctx["app_id"] = app["id"]
            sess = cli.post("/api/v1/chat/sessions", {"appId": app["id"], "clientSource": "ADMIN"})
            ctx["admin_session_id"] = sess["id"]
            ok("chat.session.create", sess["id"])

            # SSE stream (collect briefly)
            hdrs = {
                "Authorization": f"Bearer {cli.token}",
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            }
            answer_parts: list[str] = []
            sources_n = 0
            with cli.c.stream(
                "POST",
                f"{GW}/api/v1/chat/stream",
                headers=hdrs,
                json={
                    "sessionId": sess["id"],
                    "appId": app["id"],
                    "question": "用一句话介绍 NoteMind 关键能力",
                },
                timeout=180.0,
            ) as resp:
                if resp.status_code >= 400:
                    raise AssertionError(f"sse {resp.status_code}")
                event = None
                for line in resp.iter_lines():
                    if not line:
                        continue
                    if line.startswith("event:"):
                        event = line[6:].strip()
                    elif line.startswith("data:"):
                        raw = line[5:].strip()
                        try:
                            payload = json.loads(raw)
                        except Exception:
                            continue
                        if event == "delta":
                            piece = payload.get("content") or payload.get("text") or ""
                            if piece:
                                answer_parts.append(piece)
                        elif event == "final":
                            sources_n = len(payload.get("sources") or [])
                            if payload.get("content"):
                                answer_parts.append(str(payload.get("content")))
                        elif event == "done":
                            break
            text = "".join(answer_parts)
            ok("chat.sse", f"chars={len(text)} sources={sources_n}")

        # Agent run (may need model keys)
        if kb_id:
            try:
                agent = cli.post(
                    "/api/v1/agent-runs/execute",
                    {"knowledgeBaseId": kb_id, "question": "NoteMind 核心能力是什么？"},
                )
                ok(
                    "agent.execute",
                    f"status={agent.get('status')} steps={len(agent.get('steps') or [])}",
                )
            except Exception as e:
                # soft: agent depends on LLM
                skip("agent.execute", str(e)[:200])
    except Exception as e:
        fail("app.chat", str(e))

    section("清理 Admin 临时资源")
    try:
        if ctx.get("doc_id"):
            cli.delete(f"/api/v1/knowledge/documents/{ctx['doc_id']}")
            ok("cleanup.doc")
        if ctx.get("app_created"):
            cli.delete(f"/api/v1/qa-apps/{ctx['app_created']}")
            ok("cleanup.app")
        if ctx.get("kb_id"):
            cli.delete(f"/api/v1/knowledge/bases/{ctx['kb_id']}")
            ok("cleanup.kb")
        if ctx.get("prompt_id"):
            cli.delete(f"/api/v1/prompt-templates/{ctx['prompt_id']}")
            ok("cleanup.prompt")
    except Exception as e:
        fail("cleanup", str(e))
    return ctx


def run_web(cli: Client) -> None:
    section("Web 前台（demo）")
    try:
        data = cli.login("demo", "changeme")
        ok("web.login", f"role={data.get('user', {}).get('role')}")
    except Exception as e:
        fail("web.login", str(e))
        return

    try:
        me = cli.get("/api/v1/auth/me")
        ok("web.me", me.get("username") or me.get("nickname") or "")
    except Exception as e:
        fail("web.me", str(e))

    try:
        apps = cli.get("/api/v1/qa-apps")
        ok("web.qa_apps", f"n={len(apps or [])}")
        if not apps:
            skip("web.chat", "no qa apps")
            return
        app = apps[0]
        sess = cli.post("/api/v1/chat/sessions", {"appId": app["id"], "clientSource": "WEB"})
        ok("web.session", sess["id"])
        msgs = cli.get(f"/api/v1/chat/sessions/{sess['id']}/messages")
        ok("web.messages", f"n={len(msgs or [])}")

        hdrs = {
            "Authorization": f"Bearer {cli.token}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        got_final = False
        with cli.c.stream(
            "POST",
            f"{GW}/api/v1/chat/stream",
            headers=hdrs,
            json={
                "sessionId": sess["id"],
                "appId": app["id"],
                "question": "你好，请简单介绍一下自己",
            },
            timeout=180.0,
        ) as resp:
            if resp.status_code >= 400:
                raise AssertionError(f"sse {resp.status_code} {resp.read()[:200]}")
            event = None
            for line in resp.iter_lines():
                if not line:
                    continue
                if line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:") and event in {"final", "done"}:
                    if event == "final":
                        got_final = True
                    if event == "done":
                        break
        ok("web.sse", f"final={got_final}")
    except Exception as e:
        fail("web.flow", str(e))


def run_ai() -> None:
    section("AI Engine 直连")
    try:
        r = httpx.get(f"{AI}/health", timeout=10.0)
        r.raise_for_status()
        ok("ai.health", r.text[:80])
    except Exception as e:
        fail("ai.health", str(e))
        return

    headers = {"X-AI-Engine-Token": AI_TOKEN, "Content-Type": "application/json"}
    try:
        r = httpx.post(
            f"{AI}/api/v1/ai/llm/chat",
            headers=headers,
            json={
                "messages": [{"role": "user", "content": "只回复：ok"}],
                "temperature": 0,
            },
            timeout=60.0,
        )
        if r.status_code >= 400:
            skip("ai.llm", f"{r.status_code} {r.text[:160]}")
        else:
            ok("ai.llm", r.text[:80].replace("\n", " "))
    except Exception as e:
        skip("ai.llm", str(e)[:160])


def main() -> int:
    print("NoteMind E2E full-flow")
    print(f"Gateway={GW} AI={AI}")
    run_ai()
    admin = Client()
    try:
        run_admin(admin)
    finally:
        admin.close()
    web = Client()
    try:
        run_web(web)
    finally:
        web.close()

    section("汇总")
    print(f"PASS={PASS} FAIL={FAIL} SKIP={SKIP}")
    if FAIL:
        print("\n失败项：")
        for st, name, detail in RESULTS:
            if st == "FAIL":
                print(f"  - {name}: {detail}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
