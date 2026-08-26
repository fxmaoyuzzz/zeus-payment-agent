package com.moyu.zeuspaymentagent.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 暴露给 LLM 的订单视图，避免直接返回完整数据库实体。
 */
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
