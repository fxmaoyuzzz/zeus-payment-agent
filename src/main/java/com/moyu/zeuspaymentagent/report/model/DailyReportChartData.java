package com.moyu.zeuspaymentagent.report.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 支付日报前端图表数据。
 */
public record DailyReportChartData(
        LocalDate reportDate,
        long totalOrders,
        long successOrders,
        long failedOrders,
        long pendingOrders,
        List<PaymentChannelDailyStat> channelStats,
        List<PaymentFailureDailyStat> failureStats) {

    /**
     * 从日报业务视图提取图表需要的数据。
     */
    public static DailyReportChartData from(DailyPaymentReport report) {
        return new DailyReportChartData(
                report.reportDate(),
                report.totalOrders(),
                report.successOrders(),
                report.failedOrders(),
                report.pendingOrders(),
                report.channelStats(),
                report.failureStats());
    }
}
