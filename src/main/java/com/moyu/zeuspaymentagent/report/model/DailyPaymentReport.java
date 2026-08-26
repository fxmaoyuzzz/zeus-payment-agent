package com.moyu.zeuspaymentagent.report.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付日报的完整业务视图。
 */
public record DailyPaymentReport(
        LocalDate reportDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long totalOrders,
        long successOrders,
        long failedOrders,
        long pendingOrders,
        BigDecimal successRate,
        BigDecimal failureRate,
        BigDecimal totalAmount,
        List<PaymentChannelDailyStat> channelStats,
        List<PaymentFailureDailyStat> failureStats,
        PaymentLatencyDailyStat latencyStat,
        List<String> highlights) {
}
