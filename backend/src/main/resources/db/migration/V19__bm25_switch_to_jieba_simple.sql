-- V19: BM25 回退到 Java Jieba + simple 配置
-- 原因：zhparser 对代码内容分词会把标点符号当 token，tsv 噪声大
-- 策略：Java Jieba 预分词（cleanTokens 4 层过滤）+ simple 配置存储
-- 入库和查询都用 simple 配置，确保分词一致性

-- 回填存量 chunk 的 tsv（用 simple 配置从 content 重新生成）
UPDATE kb_chunks
SET tsv = to_tsvector('simple', content)
WHERE content IS NOT NULL;
