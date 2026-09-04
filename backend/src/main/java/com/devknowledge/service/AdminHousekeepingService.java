package com.devknowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 后台数据保留策略：定期清理过期的请求观测数据。
 * error_reports 与 user_feedback 有长期排查价值，永久保留，不参与清理。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminHousekeepingService {

    private final HousekeepingMapper housekeepingMapper;

    @Value("${observability.trace-retention-days:14}")
    private int retentionDays;

    /**
     * 每天凌晨 3:30 清理过期 traces 与 spans。
     * 删除失败只记日志，不影响服务，次日任务重试。
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpiredTraces() {
        try {
            int traces = housekeepingMapper.deleteExpiredTraces(retentionDays);
            int spans = housekeepingMapper.deleteExpiredSpans(retentionDays);
            log.info("观测数据清理完成：traces 删除 {} 行，spans 删除 {} 行（保留 {} 天）",
                    traces, spans, retentionDays);
        } catch (Exception e) {
            log.warn("观测数据清理失败，将于次日重试: {}", e.getMessage());
        }
    }

    /** 清理用 Mapper（独立于 AdminMapper，职责单一） */
    @Mapper
    public interface HousekeepingMapper {

        @Select("SELECT COUNT(*) FROM request_traces WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        long countExpiredTraces(@Param("retentionDays") int retentionDays);

        @Delete("DELETE FROM request_traces WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        int deleteExpiredTraces(@Param("retentionDays") int retentionDays);

        @Delete("DELETE FROM request_spans WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        int deleteExpiredSpans(@Param("retentionDays") int retentionDays);
    }
}
