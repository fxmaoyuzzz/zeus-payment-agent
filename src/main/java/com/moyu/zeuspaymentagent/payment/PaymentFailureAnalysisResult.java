package com.moyu.zeuspaymentagent.payment;

import com.moyu.zeuspaymentagent.order.PaymentOrderView;
import java.util.List;

public record PaymentFailureAnalysisResult(
        String orderNo,
        boolean orderFound,
        PaymentOrderView order,
        PaymentTransaction transaction,
        PaymentFailureRule matchedRule,
        String reasonType,
        String reasonMessage,
        String suggestion,
        String confidence,
        List<String> evidence,
        List<PaymentLog> logs) {
}
