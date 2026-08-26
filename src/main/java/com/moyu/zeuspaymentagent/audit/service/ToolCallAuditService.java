package com.moyu.zeuspaymentagent.audit.service;

import com.moyu.zeuspaymentagent.audit.mapper.ToolCallAuditMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ToolCallAuditService {

    private static final int SUMMARY_LIMIT = 4000;

    private final ToolCallAuditMapper toolCallAuditMapper;
    private final JsonMapper jsonMapper;

    public ToolCallAuditService(ToolCallAuditMapper toolCallAuditMapper, JsonMapper jsonMapper) {
        this.toolCallAuditMapper = toolCallAuditMapper;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 审计流程：序列化输入输出摘要 -> 截断超长内容 -> 写入审计表。
     */
    public void record(
            String toolName,
            String toolClass,
            String toolMethod,
            Object[] args,
            Object result,
            Throwable error,
            long latencyMs) {
        try {
            toolCallAuditMapper.insertAudit(
                    UUID.randomUUID().toString(),
                    toolName,
                    toolClass,
                    toolMethod,
                    summarize(args),
                    error == null ? summarize(result) : null,
                    error == null ? "SUCCESS" : "FAILED",
                    error == null ? null : truncate(error.getMessage()),
                    latencyMs,
                    LocalDateTime.now());
        }
        catch (Exception ignored) {
        }
    }

    private String summarize(Object value) {
        try {
            return truncate(jsonMapper.writeValueAsString(value));
        }
        catch (JacksonException ex) {
            return truncate(String.valueOf(value));
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_LIMIT) {
            return value;
        }
        return value.substring(0, SUMMARY_LIMIT);
    }
}
