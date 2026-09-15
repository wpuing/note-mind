-- 修复系统配置中文乱码（仅刷新 label/description 等展示字段，保留 config_value）
USE notemind;

UPDATE t_system_config SET
  label = '默认知识库 ID',
  description = '上传/检索/Agent 未指定知识库时使用'
WHERE config_key = 'default.knowledge_base_id';

UPDATE t_system_config SET
  label = '默认向量模型 ID',
  description = '新建知识库未指定 embedding 时使用'
WHERE config_key = 'default.embedding_model_id';

UPDATE t_system_config SET
  label = '默认切分策略 ID',
  description = '新建知识库未指定切分策略时使用'
WHERE config_key = 'default.chunk_strategy_id';

UPDATE t_system_config SET
  label = '默认检索策略 ID',
  description = '新建知识库未指定检索策略时使用'
WHERE config_key = 'default.retrieval_strategy_id';

UPDATE t_system_config SET
  label = '列表分页上限',
  description = '各分页接口 pageSize 最大值'
WHERE config_key = 'page.max_size';

UPDATE t_system_config SET
  label = '允许上传扩展名',
  description = '逗号分隔，不含点；小写'
WHERE config_key = 'upload.extensions';

UPDATE t_system_config SET
  label = 'Prompt 场景枚举',
  description = '逗号分隔；保存 Prompt 时校验',
  config_value = '问答生成,查询改写,Agentic RAG,评测集构建,效果评测'
WHERE config_key = 'prompt.scenarios';

UPDATE t_system_config SET
  label = '默认 TopK',
  description = '检索未传 top_k 时的默认值（AI 引擎同步）'
WHERE config_key = 'retrieval.default_top_k';

UPDATE t_system_config SET
  label = 'TopK 上限',
  description = '检索 top_k 允许的最大值'
WHERE config_key = 'retrieval.max_top_k';

UPDATE t_system_config SET
  label = '对话温度',
  description = '普通生成默认 temperature'
WHERE config_key = 'llm.chat_temperature';

UPDATE t_system_config SET
  label = 'Judge 温度',
  description = 'Agent/评测 Judge 建议为 0'
WHERE config_key = 'llm.judge_temperature';

UPDATE t_system_config SET
  label = '会话列表上限',
  description = '用户端会话列表 LIMIT'
WHERE config_key = 'chat.session_list_limit';

UPDATE t_system_config SET
  label = '消息列表上限',
  description = '会话消息列表 LIMIT'
WHERE config_key = 'chat.message_list_limit';
