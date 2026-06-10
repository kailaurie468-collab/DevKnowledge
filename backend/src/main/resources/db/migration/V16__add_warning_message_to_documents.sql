-- 文档表新增 warning_message 字段，用于记录非致命警告（如未向量化提示）
ALTER TABLE kb_documents ADD COLUMN IF NOT EXISTS warning_message TEXT;
