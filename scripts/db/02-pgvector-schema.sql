-- NoteMind PostgreSQL + PGVector（百炼 text-embedding-v4 = 1024 维）
-- 参考：https://www.bilibili.com/opus/1245196454660669448
CREATE EXTENSION IF NOT EXISTS vector;

-- 旧表维度若为 1536，重建为 1024（开发环境可接受；生产需迁移脚本）
DROP TABLE IF EXISTS knowledge_segment_vector;
DROP TABLE IF EXISTS knowledge_segment_bm25;

-- 子块 / 普通块向量（仅检索用小块入向量库；父块不入）
CREATE TABLE knowledge_segment_vector (
    id                VARCHAR(64) PRIMARY KEY,
    segment_id        VARCHAR(32)  NOT NULL,
    parent_segment_id VARCHAR(32),
    knowledge_base_id VARCHAR(32)  NOT NULL,
    document_id       VARCHAR(32)  NOT NULL,
    embedding_model   VARCHAR(128) NOT NULL,
    content_preview   VARCHAR(512),
    meta_json         JSONB,
    embedding         vector(1024) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ksv_segment ON knowledge_segment_vector (segment_id);
CREATE INDEX idx_ksv_parent ON knowledge_segment_vector (parent_segment_id);
CREATE INDEX idx_ksv_kb ON knowledge_segment_vector (knowledge_base_id);
CREATE INDEX idx_ksv_doc ON knowledge_segment_vector (document_id);
CREATE INDEX idx_ksv_embedding_cosine
    ON knowledge_segment_vector
    USING hnsw (embedding vector_cosine_ops);

-- BM25 / 全文检索侧文本（与向量库同库，便于混合检索联查；也可放 MySQL，此处集中）
CREATE TABLE knowledge_segment_bm25 (
    segment_id        VARCHAR(32) PRIMARY KEY,
    knowledge_base_id VARCHAR(32)  NOT NULL,
    document_id       VARCHAR(32)  NOT NULL,
    parent_segment_id VARCHAR(32),
    content           TEXT         NOT NULL,
    content_tsv       tsvector,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ksb_kb ON knowledge_segment_bm25 (knowledge_base_id);
CREATE INDEX idx_ksb_tsv ON knowledge_segment_bm25 USING GIN (content_tsv);
