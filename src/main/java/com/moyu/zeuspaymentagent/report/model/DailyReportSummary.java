package com.moyu.zeuspaymentagent.report.model;

import java.math.BigDecimal;

/**
 * 日报订单汇总统计。
 */
public class DailyReportSummary {

    private Long totalOrders;
    private Long successOrders;
    private Long failedOrders;
    private Long pendingOrders;
    private BigDecimal totalAmount;

    public Long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Long getSuccessOrders() {
        return successOrders;
    }

    public void setSuccessOrders(Long successOrders) {
        this.successOrders = successOrders;
    }

    public Long getFailedOrders() {
        return failedOrders;
    }

    public void setFailedOrders(Long failedOrders) {
        this.failedOrders = failedOrders;
    }

    public Long getPendingOrders() {
        return pendingOrders;
    }

    public void setPendingOrders(Long pendingOrders) {
        this.pendingOrders = pendingOrders;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
