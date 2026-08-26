package com.moyu.zeuspaymentagent.investigation.model;

/**
 * 渠道和失败码交叉统计。
 */
public class ChannelFailureCodeStat {

    private String methodCode;
    private String channelCode;
    private String failureCode;
    private String channelErrorCode;
    private String failureReason;
    private Long failureCount;

    public String getMethodCode() {
        return methodCode;
    }

    public void setMethodCode(String methodCode) {
        this.methodCode = methodCode;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getChannelErrorCode() {
        return channelErrorCode;
    }

    public void setChannelErrorCode(String channelErrorCode) {
        this.channelErrorCode = channelErrorCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Long failureCount) {
        this.failureCount = failureCount;
    }
}
