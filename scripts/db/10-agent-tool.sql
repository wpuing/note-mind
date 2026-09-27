-- Agent 工具中心：排序/实现标记 + 种子三套
USE notemind;

SET @db := DATABASE();

SET @exist := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_agent_tool' AND COLUMN_NAME = 'sort_no'
);
SET @sql := IF(
  @exist = 0,
  'ALTER TABLE t_agent_tool ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT ''排序，越小越靠前'' AFTER enabled',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist2 := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_agent_tool' AND COLUMN_NAME = 'implemented'
);
SET @sql2 := IF(
  @exist2 = 0,
  'ALTER TABLE t_agent_tool ADD COLUMN implemented TINYINT NOT NULL DEFAULT 1 COMMENT ''1已实现 0未实现'' AFTER sort_no',
  'SELECT 1'
);
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

INSERT INTO t_agent_tool (
  id, code, name, description, schema_json, enabled, sort_no, implemented
) VALUES
(
  'tool_search_kb', 'search_knowledge', '知识库检索',
  '在指定知识库中检索与问题相关的资料片段，返回片段内容和相关性得分。',
  CAST('{"query":"检索问题","kb_id":"知识库ID","top_k":"召回条数"}' AS JSON),
  1, 1, 1
),
(
  'tool_list_docs', 'list_documents', '文档清单查询',
  '列出指定知识库下的文档清单，可按名称关键词过滤，返回文档 ID、名称与片段数。',
  CAST('{"kb_id":"知识库ID","keyword":"文档名称关键词（可选）"}' AS JSON),
  1, 2, 0
),
(
  'tool_doc_detail', 'get_document_detail', '文档详情查询',
  '按文档 ID 查询文档元数据与摘要信息，便于 Agent 确认资料来源。',
  CAST('{"document_id":"文档ID"}' AS JSON),
  1, 3, 0
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  schema_json = VALUES(schema_json),
  enabled = VALUES(enabled),
  sort_no = VALUES(sort_no),
  implemented = VALUES(implemented),
  deleted = 0,
  delete_time = NULL;
