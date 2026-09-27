-- 清除工具调用日志演示数据（假 run_demo_* / tcl_demo_*），避免与真实 Agent 调用混淆
USE notemind;

DELETE FROM t_agent_tool_call_log
WHERE id LIKE 'tcl_demo_%'
   OR agent_run_id LIKE 'run_demo_%';
