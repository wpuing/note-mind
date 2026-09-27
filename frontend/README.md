# NoteMind Frontend

| 包 | 端口 | 职责 |
| --- | --- | --- |
| `@notemind/shared` | — | 请求封装、公共类型 |
| `@notemind/web` | 5173 | SSE 流式问答、引用溯源、会话、反馈 |
| `@notemind/admin` | 5174 | 知识库策略、AI 配置、Agent 时间线、评测 ECharts |

管理端关键视图：`knowledge/*`、`ai/*`、`app`、`chat-log`、`eval/*`。
