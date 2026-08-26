package com.moyu.zeuspaymentagent.payment.mapper;

import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.util.StringUtils;

/**
 * 支付流水动态 SQL 构造器。
 */
public class PaymentTransactionSqlProvider {

    /**
     * 按可选条件查询支付流水。
     */
    public String queryTransactions(Map<String, Object> params) {
        return new SQL()
                .SELECT("""
                        id,
                        transaction_no AS transactionNo,
                        order_no AS orderNo,
                        user_id AS userId,
                        method_code AS methodCode,
                        channel_code AS channelCode,
                        amount,
                        currency,
                        status,
                        failure_code AS failureCode,
                        failure_reason AS failureReason,
                        channel_error_code AS channelErrorCode,
                        channel_error_message AS channelErrorMessage,
                        paid_at AS paidAt,
                        created_at AS createdAt,
                        updated_at AS updatedAt
                        """)
                .FROM("payment_transaction")
                .WHERE(StringUtils.hasText((String) params.get("transactionNo")) ? "transaction_no = #{transactionNo}" : "1 = 1")
                .WHERE(StringUtils.hasText((String) params.get("orderNo")) ? "order_no = #{orderNo}" : "1 = 1")
                .WHERE(StringUtils.hasText((String) params.get("userId")) ? "user_id = #{userId}" : "1 = 1")
                .WHERE(StringUtils.hasText((String) params.get("status")) ? "status = #{status}" : "1 = 1")
                .WHERE(StringUtils.hasText((String) params.get("channelCode")) ? "channel_code = #{channelCode}" : "1 = 1")
                .WHERE(params.get("startTime") instanceof LocalDateTime ? "created_at >= #{startTime}" : "1 = 1")
                .WHERE(params.get("endTime") instanceof LocalDateTime ? "created_at < #{endTime}" : "1 = 1")
                .ORDER_BY("created_at DESC")
                .toString() + " LIMIT #{limit}";
    }
}
