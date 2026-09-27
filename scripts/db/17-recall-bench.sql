-- 召回调试台：多策略对比运行记录
USE notemind;

CREATE TABLE IF NOT EXISTS t_recall_debug_run (
    id                  VARCHAR(32)  NOT NULL,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_user         VARCHAR(32)  NULL,
    create_user_name    VARCHAR(64)  NULL,
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    update_user         VARCHAR(32)  NULL,
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    delete_time         DATETIME(3)  NULL,
    question            VARCHAR(2000) NOT NULL COMMENT '检索问题',
    knowledge_base_id   VARCHAR(32)  NOT NULL COMMENT '知识库',
    knowledge_base_name VARCHAR(128) NULL COMMENT '知识库名称快照',
    strategy_count      INT          NOT NULL DEFAULT 0 COMMENT '对比策略数',
    strategy_names_json JSON         NULL COMMENT '策略名称列表',
    document_ids_json   JSON         NULL COMMENT '限定文档',
    elapsed_ms          BIGINT       NULL COMMENT '总耗时毫秒',
    result_json         JSON         NULL COMMENT '各策略检索结果',
    PRIMARY KEY (id),
    KEY idx_recall_debug_kb_time (knowledge_base_id, create_time),
    KEY idx_recall_debug_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='召回调试台对比记录';
