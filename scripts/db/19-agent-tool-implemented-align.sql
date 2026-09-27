-- 工具中心：仅 search_knowledge 已接入执行链路；其余种子工具标为未实现，避免「已实现却无调用日志」误解
USE notemind;

UPDATE t_agent_tool
SET implemented = 0, update_time = NOW(3)
WHERE deleted = 0
  AND code IN ('list_documents', 'get_document_detail');

UPDATE t_agent_tool
SET implemented = 1, update_time = NOW(3)
WHERE deleted = 0
  AND code = 'search_knowledge';
