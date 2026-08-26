package com.moyu.zeuspaymentagent.payment.model;

import com.moyu.zeuspaymentagent.order.model.PaymentOrderView;
import java.util.List;

/**
 * 支付失败分析 Tool 返回给 LLM 的结构化结果。
 */
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
