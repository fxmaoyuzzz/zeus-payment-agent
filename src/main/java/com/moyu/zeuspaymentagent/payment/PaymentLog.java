package com.moyu.zeuspaymentagent.payment;

import java.time.LocalDateTime;

public class PaymentLog {

    private Long id;
    private String logNo;
    private String orderNo;
    private String transactionNo;
    private String methodCode;
    private String channelCode;
    private String eventType;
    private String eventStatus;
    private String channelErrorCode;
    private String channelErrorMessage;
    private Long latencyMs;
    private String traceId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogNo() {
        return logNo;
    }

    public void setLogNo(String logNo) {
        this.logNo = logNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public String getChannelErrorCode() {
        return channelErrorCode;
    }

    public void setChannelErrorCode(String channelErrorCode) {
        this.channelErrorCode = channelErrorCode;
    }

    public String getChannelErrorMessage() {
        return channelErrorMessage;
    }

    public void setChannelErrorMessage(String channelErrorMessage) {
        this.channelErrorMessage = channelErrorMessage;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
