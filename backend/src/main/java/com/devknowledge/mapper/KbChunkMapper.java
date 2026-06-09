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
     * 使用 PostgreSQL tsvector + ts_rank 实现全文检索排序
     *
     * @param kbId   知识库 ID
     * @param tsQuery Jieba 分词后拼接的 tsquery 表达式（如 "知识 & 图谱"）
     * @param topK   返回结果数上限
     * @return 按 BM25 相关性排序的 chunk 列表
     */
    @Select("SELECT c.id, c.doc_id as docId, d.filename, c.chunk_index as chunkIndex, c.content, " +
            "ts_rank(c.tsv, #{tsQuery}::tsquery) as score " +
            "FROM kb_chunks c " +
            "JOIN kb_documents d ON c.doc_id = d.id " +
            "WHERE c.kb_id = #{kbId} AND c.tsv @@ #{tsQuery}::tsquery " +
            "ORDER BY ts_rank(c.tsv, #{tsQuery}::tsquery) DESC " +
            "LIMIT #{topK}")
    List<KbChunkSearchResult> searchByBm25(
            @Param("kbId") UUID kbId,
            @Param("tsQuery") String tsQuery,
            @Param("topK") int topK);

    /**
     * 批量插入 chunk（含 tsvector 列）
     * 自动将 Jieba 分词结果通过 ::tsvector 转换存入 PostgreSQL tsvector 类型列
     */
    @InsertProvider(type = InsertBatchProvider.class, method = "provide")
    void insertBatchWithTsv(@Param("chunks") List<KbChunk> chunks);

    /**
     * 动态 SQL 生成器：为每个 chunk 生成包含 tsv 列的 INSERT 语句
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
                            "#{chunks[%d].createdAt}, #{chunks[%d].tsv}::tsvector)",
                            i, i, i, i, i, i, i, i))
                    .collect(Collectors.joining(", "));
            return "INSERT INTO kb_chunks (id, kb_id, doc_id, chunk_index, content, embedding, created_at, tsv) "
                    + "VALUES " + values;
        }
    }
}
