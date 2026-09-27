-- 用户种子由 Service 启动时 AuthAsvc.ensureSeedUsers 写入（BCrypt）：
--   admin / DEMO_ADMIN_PASSWORD（默认 changeme）→ u_admin / ADMIN
--   demo  / DEMO_PASSWORD（默认 changeme）        → u_demo  / USER
-- 本文件仅作说明，无需手动执行。
SELECT 'user seed is auto-created by AuthAsvc on startup/login' AS notice;
