package com.moyu.zeuspaymentagent.investigation.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调查过程中采集的支付流水证据。
 */
public record PaymentTransactionEvidence(
        String transactionNo,
        String orderNo,
        String userId,
        String methodCode,
        String channelCode,
        BigDecimal amount,
        String currency,
        String status,
        String failureCode,
        String failureReason,
        String channelErrorCode,
        String channelErrorMessage,
        LocalDateTime createdAt) {
}
