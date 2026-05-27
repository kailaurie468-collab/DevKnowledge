package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.model.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.UUID;

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
}
