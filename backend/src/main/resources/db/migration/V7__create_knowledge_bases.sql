-- 知识库表
CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 文档表
CREATE TABLE kb_documents (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    content TEXT,
    content_tsv TSVECTOR,
    status VARCHAR(20) DEFAULT 'processing',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kb_documents_kb_id ON kb_documents(kb_id);
CREATE INDEX idx_kb_documents_content ON kb_documents USING GIN(to_tsvector('english', content));
