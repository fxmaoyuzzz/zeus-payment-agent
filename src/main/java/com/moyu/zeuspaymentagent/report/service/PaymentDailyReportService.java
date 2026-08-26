package com.moyu.zeuspaymentagent.report.service;

import com.moyu.zeuspaymentagent.report.mapper.PaymentDailyReportMapper;
import com.moyu.zeuspaymentagent.report.model.DailyPaymentReport;
import com.moyu.zeuspaymentagent.report.model.DailyReportSummary;
import com.moyu.zeuspaymentagent.report.model.PaymentChannelDailyStat;
import com.moyu.zeuspaymentagent.report.model.PaymentFailureDailyStat;
import com.moyu.zeuspaymentagent.report.model.PaymentLatencyDailyStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PaymentDailyReportService {

    private static final int FAILURE_TOP_LIMIT = 10;

    private final PaymentDailyReportMapper paymentDailyReportMapper;
    private final JsonMapper jsonMapper;

    public PaymentDailyReportService(PaymentDailyReportMapper paymentDailyReportMapper, JsonMapper jsonMapper) {
        this.paymentDailyReportMapper = paymentDailyReportMapper;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 日报生成流程：按自然日聚合数据 -> 计算指标 -> 保存 JSON 结果。
     */
    public DailyPaymentReport generate(LocalDate reportDate) {
        var report = summarize(reportDate, true);

        paymentDailyReportMapper.upsertReport(
                report.reportDate(),
                report.totalOrders(),
                report.successOrders(),
                report.failedOrders(),
                report.pendingOrders(),
                report.successRate(),
                report.failureRate(),
                report.totalAmount(),
                toJson(report),
                java.time.LocalDateTime.now());

        return report;
    }

    /**
     * 异常调查摘要流程：只聚合订单和支付流水，不查询支付日志，不覆盖日报表。
     */
    public DailyPaymentReport summarizeWithoutLatency(LocalDate reportDate) {
        return summarize(reportDate, false);
    }

    private DailyPaymentReport summarize(LocalDate reportDate, boolean includeLatency) {
        var startTime = reportDate.atStartOfDay();
        var endTime = reportDate.plusDays(1).atStartOfDay();
        var summary = normalize(paymentDailyReportMapper.summarizeOrders(startTime, endTime));
        var channelStats = paymentDailyReportMapper.summarizeChannels(startTime, endTime);
        var failureStats = paymentDailyReportMapper.summarizeFailures(startTime, endTime, FAILURE_TOP_LIMIT);
        var latencyStat = includeLatency
                ? normalize(paymentDailyReportMapper.summarizeLatency(startTime, endTime))
                : new PaymentLatencyDailyStat();

        var totalOrders = number(summary.getTotalOrders());
        var successOrders = number(summary.getSuccessOrders());
        var failedOrders = number(summary.getFailedOrders());
        var pendingOrders = number(summary.getPendingOrders());
        var totalAmount = money(summary.getTotalAmount());

        var report = new DailyPaymentReport(
                reportDate,
                startTime,
                endTime,
                totalOrders,
                successOrders,
                failedOrders,
                pendingOrders,
                rate(successOrders, totalOrders),
                rate(failedOrders, totalOrders),
                totalAmount,
                channelStats,
                failureStats,
                latencyStat,
                buildHighlights(totalOrders, successOrders, failedOrders, pendingOrders, channelStats, failureStats, latencyStat));
        return report;
    }

    private List<String> buildHighlights(
            long totalOrders,
            long successOrders,
            long failedOrders,
            long pendingOrders,
            List<PaymentChannelDailyStat> channelStats,
            List<PaymentFailureDailyStat> failureStats,
            PaymentLatencyDailyStat latencyStat) {
        var highlights = new ArrayList<String>();
        highlights.add("订单总量 " + totalOrders + "，成功 " + successOrders + "，失败 " + failedOrders + "。");

        if (pendingOrders > 0) {
            highlights.add("仍有 " + pendingOrders + " 笔订单处于处理中，需要关注是否长时间未完成。");
        }

        channelStats.stream()
                .max(Comparator.comparing(stat -> number(stat.getFailedCount())))
                .filter(stat -> number(stat.getFailedCount()) > 0)
                .ifPresent(stat -> highlights.add("失败最多的渠道是 " + stat.getChannelCode()
                        + "，失败 " + number(stat.getFailedCount()) + " 笔。"));

        failureStats.stream()
                .findFirst()
                .ifPresent(stat -> highlights.add("Top 失败码是 " + safeText(stat.getFailureCode())
                        + "，出现 " + number(stat.getFailureCount()) + " 次。"));

        if (number(latencyStat.getTimeoutCount()) > 0) {
            highlights.add("支付日志中出现 " + number(latencyStat.getTimeoutCount()) + " 次超时事件。");
        }
        return highlights;
    }

    private DailyReportSummary normalize(DailyReportSummary summary) {
        return summary == null ? new DailyReportSummary() : summary;
    }

    private PaymentLatencyDailyStat normalize(PaymentLatencyDailyStat latencyStat) {
        return latencyStat == null ? new PaymentLatencyDailyStat() : latencyStat;
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String toJson(DailyPaymentReport report) {
        try {
            return jsonMapper.writeValueAsString(report);
        }
        catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize payment daily report", ex);
        }
    }
}
