package com.moyu.zeuspaymentagent.payment.mapper;

import java.util.Map;
import org.springframework.util.StringUtils;

public final class PaymentFailureRuleSqlProvider {

    private PaymentFailureRuleSqlProvider() {
    }

    public static String findBestRule(Map<String, Object> params) {
        var sql = new StringBuilder("""
                SELECT
                    id,
                    method_code AS methodCode,
                    channel_code AS channelCode,
                    failure_code AS failureCode,
                    channel_error_code AS channelErrorCode,
                    reason_type AS reasonType,
                    reason_message AS reasonMessage,
                    suggestion,
                    priority
                FROM payment_failure_rule
                WHERE enabled = 1
                """);

        appendNullableMatch(sql, params, "methodCode", "method_code");
        appendNullableMatch(sql, params, "channelCode", "channel_code");
        appendNullableMatch(sql, params, "failureCode", "failure_code");
        appendNullableMatch(sql, params, "channelErrorCode", "channel_error_code");

        sql.append("""
                 ORDER BY
                    CASE WHEN method_code IS NULL THEN 1 ELSE 0 END,
                    CASE WHEN channel_code IS NULL THEN 1 ELSE 0 END,
                    CASE WHEN failure_code IS NULL THEN 1 ELSE 0 END,
                    CASE WHEN channel_error_code IS NULL THEN 1 ELSE 0 END,
                    priority ASC,
                    id ASC
                LIMIT 1
                """);
        return sql.toString();
    }

    private static void appendNullableMatch(
            StringBuilder sql, Map<String, Object> params, String paramName, String columnName) {
        if (StringUtils.hasText((String) params.get(paramName))) {
            sql.append(" AND (").append(columnName).append(" = #{").append(paramName).append("} OR ")
                    .append(columnName).append(" IS NULL)");
        }
        else {
            sql.append(" AND ").append(columnName).append(" IS NULL");
        }
    }
}
