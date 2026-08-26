package com.moyu.zeuspaymentagent.investigation.model;

import java.math.BigDecimal;

/**
 * 支付异常信号，描述一次调查中识别到的异常指标。
 */
public record PaymentAnomalySignal(
        String anomalyNo,
        String anomalyType,
        String severity,
        String title,
        String description,
        String metricName,
        BigDecimal metricValue,
        BigDecimal thresholdValue,
        String dimensionType,
        String dimensionValue) {
}
