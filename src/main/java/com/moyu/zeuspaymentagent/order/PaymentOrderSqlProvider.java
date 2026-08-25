package com.moyu.zeuspaymentagent.order;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class PaymentOrderSqlProvider {

    private PaymentOrderSqlProvider() {
    }

    public static String searchOrders(Map<String, Object> params) {
        var sql = new StringBuilder("""
                SELECT
                    id,
                    order_no AS orderNo,
                    user_id AS userId,
                    amount,
                    currency,
                    status,
                    payment_channel AS paymentChannel,
                    failure_reason AS failureReason,
                    created_at AS createdAt,
                    updated_at AS updatedAt
                FROM payment_order
                WHERE 1 = 1
                """);

        if (StringUtils.hasText((String) params.get("status"))) {
            sql.append(" AND LOWER(status) = LOWER(#{status})");
        }
        if (StringUtils.hasText((String) params.get("userId"))) {
            sql.append(" AND user_id = #{userId}");
        }
        if (params.get("startTime") instanceof LocalDateTime && params.get("endTime") instanceof LocalDateTime) {
            sql.append(" AND created_at BETWEEN #{startTime} AND #{endTime}");
        }

        sql.append(" ORDER BY created_at DESC LIMIT #{limit}");
        return sql.toString();
    }
}
