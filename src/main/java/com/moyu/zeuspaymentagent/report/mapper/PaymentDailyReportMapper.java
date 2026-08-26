package com.moyu.zeuspaymentagent.report.mapper;

import com.moyu.zeuspaymentagent.report.model.DailyReportSummary;
import com.moyu.zeuspaymentagent.report.model.PaymentChannelDailyStat;
import com.moyu.zeuspaymentagent.report.model.PaymentFailureDailyStat;
import com.moyu.zeuspaymentagent.report.model.PaymentLatencyDailyStat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentDailyReportMapper {

    /**
     * 聚合日报范围内的订单总量、成功量、失败量和金额。
     */
    @Select("""
            SELECT
                COUNT(*) AS totalOrders,
                COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successOrders,
                COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failedOrders,
                COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pendingOrders,
                COALESCE(SUM(amount), 0) AS totalAmount
            FROM `order`
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            """)
    DailyReportSummary summarizeOrders(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按支付方式和渠道统计支付流水分布。
     */
    @Select("""
            SELECT
                method_code AS methodCode,
                channel_code AS channelCode,
                COUNT(*) AS totalCount,
                COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successCount,
                COALESCE(SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END), 0) AS failedCount,
                COALESCE(SUM(amount), 0) AS totalAmount
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            GROUP BY method_code, channel_code
            ORDER BY totalCount DESC, failedCount DESC
            """)
    List<PaymentChannelDailyStat> summarizeChannels(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计失败码和渠道错误码 TopN。
     */
    @Select("""
            SELECT
                failure_code AS failureCode,
                channel_error_code AS channelErrorCode,
                failure_reason AS failureReason,
                COUNT(*) AS failureCount
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
              AND status IN ('FAILED', 'CANCELLED', 'TIMEOUT')
            GROUP BY failure_code, channel_error_code, failure_reason
            ORDER BY failureCount DESC
            LIMIT #{limit}
            """)
    List<PaymentFailureDailyStat> summarizeFailures(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 从支付日志统计平均耗时、最大耗时和超时次数。
     */
    @Select("""
            SELECT
                COUNT(*) AS logCount,
                COALESCE(SUM(CASE WHEN event_status = 'TIMEOUT' THEN 1 ELSE 0 END), 0) AS timeoutCount,
                COALESCE(AVG(latency_ms), 0) AS avgLatencyMs,
                COALESCE(MAX(latency_ms), 0) AS maxLatencyMs
            FROM payment_log
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            """)
    PaymentLatencyDailyStat summarizeLatency(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 保存日报结果，同一天重复生成时覆盖旧内容。
     */
    @Insert("""
            INSERT INTO payment_daily_report
            (report_date, report_status, total_orders, success_orders, failed_orders, pending_orders,
             success_rate, failure_rate, total_amount, report_content, generated_at, created_at, updated_at)
            VALUES
            (#{reportDate}, 'GENERATED', #{totalOrders}, #{successOrders}, #{failedOrders}, #{pendingOrders},
             #{successRate}, #{failureRate}, #{totalAmount}, #{reportContent}, #{generatedAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                report_status = VALUES(report_status),
                total_orders = VALUES(total_orders),
                success_orders = VALUES(success_orders),
                failed_orders = VALUES(failed_orders),
                pending_orders = VALUES(pending_orders),
                success_rate = VALUES(success_rate),
                failure_rate = VALUES(failure_rate),
                total_amount = VALUES(total_amount),
                report_content = VALUES(report_content),
                generated_at = VALUES(generated_at),
                updated_at = VALUES(updated_at)
            """)
    int upsertReport(
            @Param("reportDate") LocalDate reportDate,
            @Param("totalOrders") long totalOrders,
            @Param("successOrders") long successOrders,
            @Param("failedOrders") long failedOrders,
            @Param("pendingOrders") long pendingOrders,
            @Param("successRate") BigDecimal successRate,
            @Param("failureRate") BigDecimal failureRate,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("reportContent") String reportContent,
            @Param("generatedAt") LocalDateTime generatedAt);
}
