package com.moyu.zeuspaymentagent.payment.mapper;

import com.moyu.zeuspaymentagent.payment.model.PaymentFailureRule;
import com.moyu.zeuspaymentagent.payment.model.PaymentLog;
import com.moyu.zeuspaymentagent.payment.model.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface PaymentFailureAnalysisMapper {

    /**
     * 查询订单最近一次支付流水，作为失败分析的主证据。
     */
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

    /**
     * 查询支付链路日志，用于还原渠道请求、回调和异常事件。
     */
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

    /**
     * 按支付方式、渠道、失败码和渠道错误码匹配最具体的归因规则。
     */
    @SelectProvider(type = PaymentFailureRuleSqlProvider.class, method = "findBestRule")
    Optional<PaymentFailureRule> findBestRule(
            @Param("methodCode") String methodCode,
            @Param("channelCode") String channelCode,
            @Param("failureCode") String failureCode,
            @Param("channelErrorCode") String channelErrorCode);
}
