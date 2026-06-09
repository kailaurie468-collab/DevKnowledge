-- V10__create_wiki_tables.sql
-- Wiki 知识图谱表结构

-- Wiki 原始文档表
CREATE TABLE wiki_documents (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_type   VARCHAR(20) NOT NULL,
    file_size   BIGINT NOT NULL,
    content     TEXT,
    status      VARCHAR(20) DEFAULT 'processing',
    error_msg   TEXT,
    source_type VARCHAR(20) DEFAULT 'upload',
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 实体表
CREATE TABLE wiki_entities (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(50) NOT NULL,
    description TEXT,
    page_path   VARCHAR(500),
    doc_id      UUID,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 关系表
CREATE TABLE wiki_relations (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    source_id   UUID NOT NULL,
    target_id   UUID NOT NULL,
    relation    VARCHAR(100) NOT NULL,
    description TEXT,
    strength    DOUBLE PRECISION DEFAULT 1.0,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 索引表
CREATE TABLE wiki_index (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    page_path   VARCHAR(500) NOT NULL,
    title       VARCHAR(300) NOT NULL,
    category    VARCHAR(50) NOT NULL,
    tags        TEXT[] DEFAULT '{}',
    summary     TEXT,
    doc_ids     UUID[] DEFAULT '{}',
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- 索引
CREATE INDEX idx_wiki_documents_user ON wiki_documents(user_id);
CREATE INDEX idx_wiki_documents_status ON wiki_documents(user_id, status);
CREATE INDEX idx_wiki_entities_user ON wiki_entities(user_id);
CREATE INDEX idx_wiki_entities_type ON wiki_entities(user_id, type);
CREATE INDEX idx_wiki_entities_doc ON wiki_entities(doc_id);
CREATE INDEX idx_wiki_relations_source ON wiki_relations(source_id);
CREATE INDEX idx_wiki_relations_target ON wiki_relations(target_id);
CREATE INDEX idx_wiki_index_user ON wiki_index(user_id);
CREATE INDEX idx_wiki_index_category ON wiki_index(user_id, category);
