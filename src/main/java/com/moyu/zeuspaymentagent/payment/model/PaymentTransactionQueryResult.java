package com.moyu.zeuspaymentagent.payment.model;

import java.util.List;

/**
 * 支付流水查询结果，包含查询条件、命中数量和流水明细。
 */
public record PaymentTransactionQueryResult(
        String transactionNo,
        String orderNo,
        String userId,
        String status,
        String channelCode,
        String startDate,
        String endDate,
        int count,
        List<PaymentTransaction> transactions) {
}
