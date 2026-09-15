-- NoteMind MySQL 业务库（Agentic RAG 高级企业知识库）
-- 参考：https://www.bilibili.com/opus/1245196454660669448
CREATE DATABASE IF NOT EXISTS notemind DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE notemind;

-- ========== 用户 ==========
CREATE TABLE IF NOT EXISTS t_user (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    username         VARCHAR(64)  NOT NULL,
    password         VARCHAR(128) NOT NULL,
    nickname         VARCHAR(64)  NULL,
    avatar_url       VARCHAR(512) NULL,
    role             VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- ========== AI 模型（对话 / 向量 / 重排 三类）==========
CREATE TABLE IF NOT EXISTS t_ai_model_config (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    model_type       VARCHAR(32)  NOT NULL COMMENT 'CHAT / EMBEDDING / RERANK',
    provider         VARCHAR(64)  NOT NULL DEFAULT 'dashscope' COMMENT '百炼等',
    model_name       VARCHAR(128) NOT NULL COMMENT '如 qwen-plus / text-embedding-v4 / gte-rerank-v2',
    base_url         VARCHAR(512) NULL COMMENT 'OpenAI 兼容或原生接口地址',
    api_key_enc      VARCHAR(512) NULL COMMENT '加密存储；页面仅脱敏展示',
    api_style        VARCHAR(32)  NOT NULL DEFAULT 'openai_compatible' COMMENT 'openai_compatible / dashscope_native',
    dimension        INT          NULL COMMENT 'Embedding 维度，百炼 text-embedding-v4=1024',
    temperature      DECIMAL(4,2) NULL,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    remark           VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_model_type (model_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置';

-- ========== Prompt 模板（改完立刻生效）==========
CREATE TABLE IF NOT EXISTS t_prompt_template (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    code             VARCHAR(64)  NOT NULL COMMENT '业务编码，如 RAG_ANSWER / QUERY_REWRITE',
    name             VARCHAR(128) NOT NULL,
    scenario         VARCHAR(64)  NULL COMMENT '使用场景：问答生成/查询改写/Agentic RAG/评测集构建/效果评测',
    content          MEDIUMTEXT   NOT NULL,
    variables_json   JSON         NULL COMMENT '占位变量列表，如 ["context","question"]',
    enabled          TINYINT      NOT NULL DEFAULT 1,
    remark           VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_code (code),
    KEY idx_prompt_scenario (scenario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt模板';

-- ========== 切分策略（递归分块 / 父子分块）==========
CREATE TABLE IF NOT EXISTS t_chunk_strategy (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    strategy_type    VARCHAR(32)  NOT NULL COMMENT 'RECURSIVE / PARENT_CHILD / FIXED',
    chunk_size       INT          NOT NULL DEFAULT 500,
    chunk_overlap    INT          NOT NULL DEFAULT 50,
    parent_chunk_size INT         NULL COMMENT '父子分块：父块尺寸',
    child_chunk_size  INT         NULL COMMENT '父子分块：子块尺寸',
    child_overlap     INT         NULL,
    separators_json  JSON         NULL COMMENT '递归分隔符列表',
    enabled          TINYINT      NOT NULL DEFAULT 1,
    is_default       TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统默认策略',
    remark           VARCHAR(512) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='切分策略';

-- ========== 检索策略（七开关 + 两档阈值）==========
CREATE TABLE IF NOT EXISTS t_retrieval_strategy (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    enable_vector    TINYINT      NOT NULL DEFAULT 1 COMMENT '向量检索',
    enable_bm25      TINYINT      NOT NULL DEFAULT 1 COMMENT 'BM25',
    enable_rrf       TINYINT      NOT NULL DEFAULT 1 COMMENT 'RRF 融合',
    enable_rerank    TINYINT      NOT NULL DEFAULT 1 COMMENT '重排',
    enable_rewrite   TINYINT      NOT NULL DEFAULT 1 COMMENT '查询改写',
    enable_parent_fill TINYINT    NOT NULL DEFAULT 1 COMMENT '父块回填',
    top_k            INT          NOT NULL DEFAULT 10,
    rerank_top_n     INT          NOT NULL DEFAULT 4 COMMENT '重排保留条数',
    vector_top_k     INT          NOT NULL DEFAULT 20,
    bm25_top_k       INT          NOT NULL DEFAULT 20,
    rrf_k            INT          NOT NULL DEFAULT 60 COMMENT 'RRF 常数',
    cosine_threshold DECIMAL(8,4) NULL COMMENT '余弦相似度阈值（量纲显式）',
    rerank_threshold DECIMAL(8,4) NULL COMMENT '重排相关分阈值（量纲显式）',
    rewrite_mode     VARCHAR(64)  NULL DEFAULT 'multi_query' COMMENT 'multi_query / coref / hyde / all',
    rewrite_count    INT          NOT NULL DEFAULT 3 COMMENT '多查询扩展条数',
    enabled          TINYINT      NOT NULL DEFAULT 1,
    is_default       TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统默认策略',
    remark           VARCHAR(512) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检索策略';

-- ========== 知识库分类词表 ==========
CREATE TABLE IF NOT EXISTS t_knowledge_category (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    code             VARCHAR(64)  NOT NULL COMMENT '写入 t_knowledge_base.category 的编码',
    name             VARCHAR(128) NOT NULL COMMENT '展示名',
    sort_no          INT          NOT NULL DEFAULT 0,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库分类词表';

-- ========== 知识库 ==========
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    category         VARCHAR(64)  NULL COMMENT '企业资料一级分类',
    description      VARCHAR(512) NULL,
    embedding_model_id VARCHAR(32) NOT NULL,
    chunk_strategy_id  VARCHAR(32) NOT NULL,
    retrieval_strategy_id VARCHAR(32) NULL COMMENT '检索策略',
    status           TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_kb_embed (embedding_model_id),
    KEY idx_kb_chunk (chunk_strategy_id),
    KEY idx_kb_retrieval (retrieval_strategy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

-- ========== 知识文档 ==========
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    knowledge_base_id VARCHAR(32) NOT NULL,
    title            VARCHAR(256) NOT NULL,
    file_name        VARCHAR(256) NOT NULL,
    file_type        VARCHAR(16)  NOT NULL COMMENT 'PDF/DOCX/XLSX/MD/TXT',
    file_path        VARCHAR(512) NOT NULL,
    file_size        BIGINT       NULL,
    meta_json        JSON         NULL COMMENT '元数据过滤字段',
    parse_status     VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED'
        COMMENT 'UPLOADED/PARSING/PARSED/CHUNKED/EMBEDDING/READY/FAILED',
    parse_text       LONGTEXT     NULL,
    ocr_used         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否 RapidOCR',
    error_message    VARCHAR(1024) NULL,
    PRIMARY KEY (id),
    KEY idx_doc_kb (knowledge_base_id),
    KEY idx_doc_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档';

-- ========== 知识片段（支持父子块）==========
CREATE TABLE IF NOT EXISTS t_knowledge_segment (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    knowledge_base_id VARCHAR(32) NOT NULL,
    document_id      VARCHAR(32)  NOT NULL,
    parent_id        VARCHAR(32)  NULL COMMENT '子块指向父块；父块为空',
    segment_type     VARCHAR(16)  NOT NULL DEFAULT 'CHUNK' COMMENT 'PARENT / CHILD / CHUNK',
    segment_index    INT          NOT NULL DEFAULT 0,
    content          TEXT         NOT NULL,
    content_tokens   INT          NULL,
    meta_json        JSON         NULL,
    vector_status    VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/SKIPPED/FAILED；仅子块/普通块向量化',
    vector_id        VARCHAR(64)  NULL,
    manually_edited  TINYINT      NOT NULL DEFAULT 0 COMMENT '人工修正标记',
    PRIMARY KEY (id),
    KEY idx_seg_doc (document_id),
    KEY idx_seg_kb (knowledge_base_id),
    KEY idx_seg_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识片段';

-- ========== 工具中心 ==========
CREATE TABLE IF NOT EXISTS t_agent_tool (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(512) NULL,
    schema_json      JSON         NULL COMMENT '入参说明：{"query":"检索问题"}，预览时转为 OpenAI parameters',
    enabled          TINYINT      NOT NULL DEFAULT 1 COMMENT '真开关',
    sort_no          INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    implemented      TINYINT      NOT NULL DEFAULT 1 COMMENT '1已实现 0未实现',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_code (code),
    KEY idx_tool_sort (sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent工具';

CREATE TABLE IF NOT EXISTS t_agent_tool_call_log (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    tool_id          VARCHAR(32)  NULL,
    tool_code        VARCHAR(64)  NOT NULL,
    agent_run_id     VARCHAR(32)  NULL,
    input_json       JSON         NULL,
    output_json      JSON         NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'OK',
    latency_ms       INT          NULL,
    error_message    VARCHAR(1024) NULL,
    PRIMARY KEY (id),
    KEY idx_tool_call_run (agent_run_id),
    KEY idx_tool_call_code (tool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用日志';

-- ========== Agent 运行（可复盘）==========
CREATE TABLE IF NOT EXISTS t_agent_run (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    app_id           VARCHAR(32)  NULL,
    session_id       VARCHAR(32)  NULL,
    question         TEXT         NOT NULL,
    final_answer     MEDIUMTEXT   NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    total_latency_ms INT          NULL,
    step_count       INT          NOT NULL DEFAULT 0,
    retrieval_rounds INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_agent_run_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent运行主记录';

CREATE TABLE IF NOT EXISTS t_agent_step (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    agent_run_id     VARCHAR(32)  NOT NULL,
    step_index       INT          NOT NULL,
    node_name        VARCHAR(64)  NOT NULL COMMENT 'retrieve/evaluate/rewrite/generate/reflect 等',
    input_json       JSON         NULL,
    output_json      JSON         NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'OK',
    latency_ms       INT          NULL,
    score_scale      VARCHAR(32)  NULL COMMENT 'cosine/bm25/rrf/rerank 量纲标注',
    PRIMARY KEY (id),
    KEY idx_agent_step_run (agent_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行步骤';

-- ========== 问答应用 ==========
CREATE TABLE IF NOT EXISTS t_qa_app (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(512) NULL,
    knowledge_base_id VARCHAR(32) NOT NULL,
    retrieval_strategy_id VARCHAR(32) NOT NULL,
    chat_model_id    VARCHAR(32)  NOT NULL,
    answer_prompt_id VARCHAR(32)  NULL,
    enable_agentic   TINYINT      NOT NULL DEFAULT 1,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答应用';

-- ========== 会话 / 消息 / 反馈 ==========
CREATE TABLE IF NOT EXISTS t_chat_session (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    user_id          VARCHAR(32)  NOT NULL,
    app_id           VARCHAR(32)  NOT NULL,
    title            VARCHAR(256) NULL,
    PRIMARY KEY (id),
    KEY idx_session_user (user_id),
    KEY idx_session_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

CREATE TABLE IF NOT EXISTS t_chat_message (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    session_id       VARCHAR(32)  NOT NULL,
    role             VARCHAR(16)  NOT NULL COMMENT 'USER / ASSISTANT',
    content          MEDIUMTEXT   NOT NULL,
    rewritten_query  TEXT         NULL COMMENT '指代消解后的完整问题',
    sources_json     JSON         NULL COMMENT '引用溯源片段',
    agent_run_id     VARCHAR(32)  NULL,
    feedback         VARCHAR(16)  NULL COMMENT 'LIKE / DISLIKE',
    feedback_reason  VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_msg_session (session_id),
    KEY idx_msg_feedback (feedback)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息';

-- ========== 检索测试 / 召回调试（可选落库）==========
CREATE TABLE IF NOT EXISTS t_retrieval_debug_run (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    question         TEXT         NOT NULL,
    knowledge_base_id VARCHAR(32) NOT NULL,
    strategies_json  JSON         NOT NULL COMMENT '最多四套策略并排',
    result_json      JSON         NULL COMMENT '每阶段召回数/耗时/分数量纲',
    total_latency_ms INT          NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='召回调试台运行记录';

-- ========== 评测 ==========
CREATE TABLE IF NOT EXISTS t_eval_dataset (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    name             VARCHAR(128) NOT NULL,
    source_type      VARCHAR(32)  NOT NULL COMMENT 'MANUAL / FROM_DOC / FROM_DISLIKE',
    description      VARCHAR(512) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测集';

CREATE TABLE IF NOT EXISTS t_eval_case (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    dataset_id       VARCHAR(32)  NOT NULL,
    question         TEXT         NOT NULL,
    expected_answer  MEDIUMTEXT   NULL,
    source_segment_ids JSON       NULL COMMENT '精确到单条片段，供 Context Recall 集合运算',
    knowledge_base_id VARCHAR(32) NULL,
    document_id      VARCHAR(32)  NULL,
    PRIMARY KEY (id),
    KEY idx_case_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测用例';

CREATE TABLE IF NOT EXISTS t_eval_report (
    id               VARCHAR(32)  NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user      VARCHAR(32)  NULL,
    create_user_name VARCHAR(64)  NULL,
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user      VARCHAR(32)  NULL,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time      DATETIME(3)  NULL,
    dataset_id       VARCHAR(32)  NOT NULL,
    retrieval_strategy_id VARCHAR(32) NOT NULL,
    judge_model_id   VARCHAR(32)  NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    case_count       INT          NOT NULL DEFAULT 0,
    context_recall   DECIMAL(8,4) NULL,
    context_precision DECIMAL(8,4) NULL,
    faithfulness     DECIMAL(8,4) NULL,
    answer_relevancy DECIMAL(8,4) NULL,
    overall_score    DECIMAL(8,4) NULL,
    detail_json      JSON         NULL,
    PRIMARY KEY (id),
    KEY idx_report_dataset (dataset_id),
    KEY idx_report_strategy (retrieval_strategy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测报告';
