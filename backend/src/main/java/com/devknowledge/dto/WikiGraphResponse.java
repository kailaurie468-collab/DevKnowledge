package com.devknowledge.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class WikiGraphResponse {
    private List<EntityNode> entities;
    private List<RelationEdge> relations;

    @Data
    public static class EntityNode {
        private UUID id;
        private String name;
        private String type;
        private String description;
        private String pagePath;
    }

    @Data
    public static class RelationEdge {
        private UUID sourceId;
        private UUID targetId;
        private String relation;
        private String description;
        private Double strength;
    }
}
