package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.WikiGraphResponse;
import com.devknowledge.mapper.WikiEntityMapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.mapper.WikiRelationMapper;
import com.devknowledge.model.WikiEntity;
import com.devknowledge.model.WikiIndex;
import com.devknowledge.model.WikiRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiGraphService {

    private final WikiEntityMapper wikiEntityMapper;
    private final WikiRelationMapper wikiRelationMapper;
    private final WikiIndexMapper wikiIndexMapper;

    /**
     * 获取图谱数据（实体 + 关系）
     */
    public Mono<WikiGraphResponse> getGraphData(UUID userId) {
        return Mono.fromCallable(() -> {
            // 查询实体
            List<WikiEntity> entities = wikiEntityMapper.selectList(
                    new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getUserId, userId));

            // 查询关系
            List<WikiRelation> relations = wikiRelationMapper.selectList(
                    new LambdaQueryWrapper<WikiRelation>().eq(WikiRelation::getUserId, userId));

            // 构建响应
            WikiGraphResponse response = new WikiGraphResponse();
            response.setEntities(entities.stream().map(e -> {
                WikiGraphResponse.EntityNode node = new WikiGraphResponse.EntityNode();
                node.setId(e.getId());
                node.setName(e.getName());
                node.setType(e.getType());
                node.setDescription(e.getDescription());
                node.setPagePath(e.getPagePath());
                return node;
            }).collect(Collectors.toList()));

            response.setRelations(relations.stream().map(r -> {
                WikiGraphResponse.RelationEdge edge = new WikiGraphResponse.RelationEdge();
                edge.setSourceId(r.getSourceId());
                edge.setTargetId(r.getTargetId());
                edge.setRelation(r.getRelation());
                edge.setDescription(r.getDescription());
                edge.setStrength(r.getStrength());
                return edge;
            }).collect(Collectors.toList()));

            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取索引条目列表
     */
    public Mono<List<WikiIndex>> getIndexEntries(UUID userId, String category) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<WikiIndex> wrapper = new LambdaQueryWrapper<WikiIndex>()
                    .eq(WikiIndex::getUserId, userId);
            if (category != null && !category.isEmpty()) {
                wrapper.eq(WikiIndex::getCategory, category);
            }
            wrapper.orderByDesc(WikiIndex::getUpdatedAt);
            return wikiIndexMapper.selectList(wrapper);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
