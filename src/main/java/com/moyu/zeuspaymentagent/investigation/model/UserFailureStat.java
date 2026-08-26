package com.moyu.zeuspaymentagent.investigation.model;

import java.math.BigDecimal;

/**
 * 用户维度失败集中度统计。
 */
public class UserFailureStat {

    private String userId;
    private Long totalCount;
    private Long failedCount;
    private BigDecimal failureAmount;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public BigDecimal getFailureAmount() {
        return failureAmount;
    }

    public void setFailureAmount(BigDecimal failureAmount) {
        this.failureAmount = failureAmount;
    }
}
