package com.moyu.zeuspaymentagent.audit.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ToolCallAuditMapper {

    /**
     * 保存一次 Tool 调用审计记录。
     */
    @Insert("""
            INSERT INTO tool_call_audit
            (trace_id, tool_name, tool_class, tool_method, request_summary, response_summary,
             status, error_message, latency_ms, created_at)
            VALUES
            (#{traceId}, #{toolName}, #{toolClass}, #{toolMethod}, #{requestSummary}, #{responseSummary},
             #{status}, #{errorMessage}, #{latencyMs}, #{createdAt})
            """)
    int insertAudit(
            @Param("traceId") String traceId,
            @Param("toolName") String toolName,
            @Param("toolClass") String toolClass,
            @Param("toolMethod") String toolMethod,
            @Param("requestSummary") String requestSummary,
            @Param("responseSummary") String responseSummary,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("latencyMs") long latencyMs,
            @Param("createdAt") LocalDateTime createdAt);
}
