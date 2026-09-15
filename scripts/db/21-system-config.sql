-- 系统配置：高频默认值可在管理端修改（覆盖 application.yml / .env 默认）
USE notemind;

CREATE TABLE IF NOT EXISTS t_system_config (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    config_group     VARCHAR(64)  NOT NULL DEFAULT 'general' COMMENT '分组：knowledge/retrieval/llm/prompt/upload/chat',
    config_key       VARCHAR(128) NOT NULL COMMENT '配置键，如 default.knowledge_base_id',
    config_value     TEXT         NULL,
    value_type       VARCHAR(32)  NOT NULL DEFAULT 'string' COMMENT 'string/int/decimal/csv/bool',
    label            VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名称',
    description      VARCHAR(512) NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    editable         TINYINT      NOT NULL DEFAULT 1 COMMENT '1可改 0只读展示',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key),
    KEY idx_system_config_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

INSERT INTO t_system_config (
  id, config_group, config_key, config_value, value_type, label, description, sort_order, editable
) VALUES
('sc_kb', 'knowledge', 'default.knowledge_base_id', 'kb_default', 'string',
 '默认知识库 ID', '上传/检索/Agent 未指定知识库时使用', 10, 1),
('sc_embed', 'knowledge', 'default.embedding_model_id', 'm_emb_v4', 'string',
 '默认向量模型 ID', '新建知识库未指定 embedding 时使用', 20, 1),
('sc_chunk', 'knowledge', 'default.chunk_strategy_id', 'cs_recursive', 'string',
 '默认切分策略 ID', '新建知识库未指定切分策略时使用', 30, 1),
('sc_rs', 'knowledge', 'default.retrieval_strategy_id', 'rs_baseline_vector', 'string',
 '默认检索策略 ID', '新建知识库未指定检索策略时使用', 40, 1),
('sc_page', 'knowledge', 'page.max_size', '100', 'int',
 '列表分页上限', '各分页接口 pageSize 最大值', 50, 1),
('sc_ext', 'upload', 'upload.extensions', 'pdf,txt,md,markdown,docx,xlsx,pptx', 'csv',
 '允许上传扩展名', '逗号分隔，不含点；小写', 10, 1),
('sc_scene', 'prompt', 'prompt.scenarios', '问答生成,查询改写,Agentic RAG,评测集构建,效果评测', 'csv',
 'Prompt 场景枚举', '逗号分隔；保存 Prompt 时校验', 10, 1),
('sc_topk', 'retrieval', 'retrieval.default_top_k', '5', 'int',
 '默认 TopK', '检索未传 top_k 时的默认值（AI 引擎同步）', 10, 1),
('sc_maxtop', 'retrieval', 'retrieval.max_top_k', '50', 'int',
 'TopK 上限', '检索 top_k 允许的最大值', 20, 1),
('sc_chat_t', 'llm', 'llm.chat_temperature', '0.3', 'decimal',
 '对话温度', '普通生成默认 temperature', 10, 1),
('sc_judge_t', 'llm', 'llm.judge_temperature', '0', 'decimal',
 'Judge 温度', 'Agent/评测 Judge 建议为 0', 20, 1),
('sc_sess', 'chat', 'chat.session_list_limit', '100', 'int',
 '会话列表上限', '用户端会话列表 LIMIT', 10, 1),
('sc_msg', 'chat', 'chat.message_list_limit', '500', 'int',
 '消息列表上限', '会话消息列表 LIMIT', 20, 1)
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  description = VALUES(description),
  sort_order = VALUES(sort_order),
  value_type = VALUES(value_type),
  editable = VALUES(editable);
