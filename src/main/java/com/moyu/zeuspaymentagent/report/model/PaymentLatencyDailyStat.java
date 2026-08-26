package com.moyu.zeuspaymentagent.report.model;

import java.math.BigDecimal;

/**
 * 支付日志耗时统计。
 */
public class PaymentLatencyDailyStat {

    private Long logCount;
    private Long timeoutCount;
    private BigDecimal avgLatencyMs;
    private Long maxLatencyMs;

    public Long getLogCount() {
        return logCount;
    }

    public void setLogCount(Long logCount) {
        this.logCount = logCount;
    }

    public Long getTimeoutCount() {
        return timeoutCount;
    }

    public void setTimeoutCount(Long timeoutCount) {
        this.timeoutCount = timeoutCount;
    }

    public BigDecimal getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(BigDecimal avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public Long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(Long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }
}
