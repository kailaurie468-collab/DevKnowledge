-- V17: 知识库列表拖动排序
ALTER TABLE knowledge_bases ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
CREATE INDEX idx_knowledge_bases_user_sort ON knowledge_bases(user_id, sort_order);
