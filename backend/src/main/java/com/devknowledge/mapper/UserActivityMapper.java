package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.UserActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户行为记录 Mapper
 * 含自定义聚合查询：topFrameworks / topKeywords
 */
@Mapper
public interface UserActivityMapper extends BaseMapper<UserActivity> {

    /**
     * 获取用户近 N 天使用最多的框架（降序）
     */
    @Select("SELECT framework, COUNT(*) as cnt FROM user_activities " +
            "WHERE user_id = #{userId} AND created_at > #{since} AND framework IS NOT NULL " +
            "GROUP BY framework ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopFrameworks(UUID userId, Instant since, int limit);

    /**
     * 获取用户近 N 天出现最多的关键词（降序，展开 TEXT[] 数组）
     */
    @Select("SELECT unnest(keywords) as kw, COUNT(*) as cnt FROM user_activities " +
            "WHERE user_id = #{userId} AND created_at > #{since} AND keywords IS NOT NULL " +
            "GROUP BY kw ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopKeywords(UUID userId, Instant since, int limit);
}
