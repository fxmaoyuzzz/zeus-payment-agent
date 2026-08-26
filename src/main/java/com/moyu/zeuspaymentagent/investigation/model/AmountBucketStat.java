package com.moyu.zeuspaymentagent.investigation.model;

import java.math.BigDecimal;

/**
 * 金额区间失败分布统计。
 */
public class AmountBucketStat {

    private String amountBucket;
    private Long totalCount;
    private Long failedCount;
    private BigDecimal failureRate;

    public String getAmountBucket() {
        return amountBucket;
    }

    public void setAmountBucket(String amountBucket) {
        this.amountBucket = amountBucket;
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
