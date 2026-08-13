package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.model.KbChunk;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.builder.annotation.ProviderContext;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    @Select("SELECT c.id, c.doc_id as docId, d.filename, c.chunk_index as chunkIndex, c.content, " +
            "1 - (c.embedding <=> #{vector}::vector) as score " +
            "FROM kb_chunks c " +
            "JOIN kb_documents d ON c.doc_id = d.id " +
            "WHERE c.kb_id = #{kbId} " +
            "ORDER BY c.embedding <=> #{vector}::vector " +
            "LIMIT #{topK}")
    List<KbChunkSearchResult> searchByVector(
            @Param("kbId") UUID kbId,
            @Param("vector") String vectorLiteral,
            @Param("topK") int topK);

    @Update("UPDATE kb_chunks SET embedding = #{vector}::vector WHERE id = #{id}")
    void updateVectorById(@Param("id") UUID id, @Param("vector") String vectorLiteral);

    /**
     * BM25 关键词检索
     * <p>
     * tsQuery 必须由 {@code JiebaSegmenter.buildOrTsQuery()} 预先构建，与入库分词保持一致：
     * 入库时 tsv 存的是 Jieba 词项，若这里直接用原文交给 PostgreSQL，
     * simple 配置不切中文，整串汉字会变成单个 lexeme 而永远匹配不上。
     * <p>
     * ts_rank_cd 归一化参数 33 = 1|32：1 按文档长度对数归一（抵消长 chunk 天然得分高），
     * 32 将得分压缩到 0~1 便于展示。
     *
     * @param kbId    知识库 ID
     * @param tsQuery OR 语义的 tsquery 表达式，如 "'知识' | '图谱'"
     * @param topK    返回结果数上限
     * @return 按相关性排序的 chunk 列表
     */
    @Select("SELECT c.id, c.doc_id as docId, d.filename, c.chunk_index as chunkIndex, c.content, " +
            "ts_rank_cd(c.tsv, to_tsquery('simple', #{tsQuery}), 33) as score " +
            "FROM kb_chunks c " +
            "JOIN kb_documents d ON c.doc_id = d.id " +
            "WHERE c.kb_id = #{kbId} AND c.tsv @@ to_tsquery('simple', #{tsQuery}) " +
            "ORDER BY ts_rank_cd(c.tsv, to_tsquery('simple', #{tsQuery}), 33) DESC " +
            "LIMIT #{topK}")
    List<KbChunkSearchResult> searchByBm25(
            @Param("kbId") UUID kbId,
            @Param("tsQuery") String tsQuery,
            @Param("topK") int topK);

    /**
     * 重建单个 chunk 的 tsv
     * 用于回填存量数据：V19 迁移直接用原始 content 生成 tsv，中文未经 Jieba 切分，与查询侧不一致
     *
     * @param id  chunk ID
     * @param tsv Jieba 分词后的空格分隔词项
     */
    @Update("UPDATE kb_chunks SET tsv = to_tsvector('simple', #{tsv}) WHERE id = #{id}")
    void updateTsvById(@Param("id") UUID id, @Param("tsv") String tsv);

    /**
     * 批量插入 chunk（含 tsvector 列）
     * 自动将 Jieba 分词结果通过 ::tsvector 转换存入 PostgreSQL tsvector 类型列
     */
    @InsertProvider(type = InsertBatchProvider.class, method = "provide")
    void insertBatchWithTsv(@Param("chunks") List<KbChunk> chunks);

    /**
     * 动态 SQL 生成器：为每个 chunk 生成包含 tsv 列的 INSERT 语句
     * tsv 由 Java Jieba 预分词（cleanTokens 过滤管道），通过 to_tsvector('simple', tsv) 转换
     * 入库和查询都用 simple 配置，确保分词一致性
     */
    class InsertBatchProvider {
        public static String provide(@Param("chunks") List<KbChunk> chunks) {
            if (chunks == null || chunks.isEmpty()) {
                return "SELECT 1 WHERE FALSE";
            }
            String values = IntStream.range(0, chunks.size())
                    .mapToObj(i -> String.format(
                            "(#{chunks[%d].id}::uuid, #{chunks[%d].kbId}::uuid, #{chunks[%d].docId}::uuid, " +
                            "#{chunks[%d].chunkIndex}, #{chunks[%d].content}, #{chunks[%d].embedding}::vector, " +
                            "#{chunks[%d].createdAt}, to_tsvector('simple', #{chunks[%d].tsv}))",
                            i, i, i, i, i, i, i, i))
                    .collect(Collectors.joining(", "));
            return "INSERT INTO kb_chunks (id, kb_id, doc_id, chunk_index, content, embedding, created_at, tsv) "
                    + "VALUES " + values;
        }
    }
}
