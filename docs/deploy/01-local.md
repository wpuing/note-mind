# 01 — 本地部署（Java 主业务 + Python AI）

1. 配置仓库根目录 `.env`（参考 `.env.example`；本机口令以实际为准）  
2. 依赖：本机 MySQL / PostgreSQL(PGVector) / Redis，或 `docker compose up -d`  
3. Java：`cd platform && mvn -pl notemind-service,notemind-gateway -am package -DskipTests`，启动 Gateway `:8080`、Service `:8081`  
4. Python AI：`cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8000`  
5. 前端：`cd frontend && pnpm install && pnpm dev:web` / `pnpm dev:admin`  

注意：实现 SecurityConfig 前，直接启动带 `spring-security` 的 Service 会默认拦接口，需先补白名单或临时关闭。
