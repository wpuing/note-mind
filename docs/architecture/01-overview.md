# 01 — 混合架构（Java + Python AI）

```text
web/admin → gateway:8080 → service:8081 ──MySQL──► notemind
                              │
                              └──AiEngineClient──► backend:8000 ──PGVector──► notemind_vector
```

| 组件 | 语言 | 职责 |
| --- | --- | --- |
| platform | Java | 业务、鉴权、CRUD、编排 |
| backend | Python | LangChain/LangGraph、检索与生成 |
| frontend | Vue3 | 管理端 / 用户端 |

详见根目录 `README.md`、`开发约定文档`。
