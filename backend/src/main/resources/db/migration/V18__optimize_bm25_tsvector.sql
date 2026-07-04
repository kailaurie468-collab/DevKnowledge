-- V18: BM25 检索优化（zhparser 中文分词）
-- 安装 zhparser 扩展，创建 chinese 全文检索配置，回填存量 tsv

-- 安装 zhparser 扩展
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 创建基于 zhparser 的中文全文检索配置
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese;
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);

-- 添加词性映射（覆盖 zhparser 所有词性，确保英文技术关键字不被过滤）
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR
  n,v,a,i,l,d,f,e,b,c,g,h,j,k,m,o,p,q,r,s,t,u,x,y,z
  WITH simple;

-- 回填存量 chunk 的 tsv（用 chinese 配置从 content 重新生成）
UPDATE kb_chunks
SET tsv = to_tsvector('chinese', content)
WHERE content IS NOT NULL;
