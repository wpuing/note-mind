-- 知识库分类词表（增量；完整建库见 01-mysql-schema.sql）
USE notemind;

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

INSERT INTO t_knowledge_category (id, code, name, sort_no, status) VALUES
('cat_enterprise', 'enterprise', '企业资料', 10, 1),
('cat_policy', 'policy', '制度政策', 20, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_no = VALUES(sort_no), status = VALUES(status);
