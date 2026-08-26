package com.moyu.zeuspaymentagent.investigation.model;

import java.math.BigDecimal;

/**
 * 小时级支付分布统计。
 */
public class HourlyPaymentStat {

    private Integer hourOfDay;
    private Long totalCount;
    private Long failedCount;
    private BigDecimal failureRate;

    public Integer getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(Integer hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public BigDecimal getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(BigDecimal failureRate) {
        this.failureRate = failureRate;
    }
}
