package com.moyu.zeuspaymentagent.payment;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface PaymentFailureAnalysisMapper {

    @Select("""
            SELECT
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
            FROM payment_transaction
            WHERE order_no = #{orderNo}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<PaymentTransaction> findLatestTransactionByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT
                id,
                log_no AS logNo,
                order_no AS orderNo,
                transaction_no AS transactionNo,
                method_code AS methodCode,
                channel_code AS channelCode,
                event_type AS eventType,
                event_status AS eventStatus,
                channel_error_code AS channelErrorCode,
                channel_error_message AS channelErrorMessage,
                latency_ms AS latencyMs,
                trace_id AS traceId,
                created_at AS createdAt
            FROM payment_log
            WHERE order_no = #{orderNo}
              AND (#{transactionNo} IS NULL OR transaction_no = #{transactionNo})
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<PaymentLog> findLogsByOrderNoAndTransactionNo(
            @Param("orderNo") String orderNo,
            @Param("transactionNo") String transactionNo,
            @Param("limit") int limit);

    @SelectProvider(type = PaymentFailureRuleSqlProvider.class, method = "findBestRule")
    Optional<PaymentFailureRule> findBestRule(
            @Param("methodCode") String methodCode,
            @Param("channelCode") String channelCode,
            @Param("failureCode") String failureCode,
            @Param("channelErrorCode") String channelErrorCode);
}
