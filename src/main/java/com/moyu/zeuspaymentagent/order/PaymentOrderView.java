package com.moyu.zeuspaymentagent.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrderView(
        String orderNo,
        String userId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentChannel,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PaymentOrderView from(PaymentOrder order) {
        return new PaymentOrderView(
                order.getOrderNo(),
                order.getUserId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getPaymentChannel(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}

