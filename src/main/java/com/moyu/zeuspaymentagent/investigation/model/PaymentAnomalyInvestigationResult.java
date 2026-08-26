package com.moyu.zeuspaymentagent.investigation.model;

import com.moyu.zeuspaymentagent.report.model.DailyPaymentReport;
import java.time.LocalDate;
import java.util.List;

/**
 * 支付异常自动调查结果。
 */
public record PaymentAnomalyInvestigationResult(
        String investigationNo,
        LocalDate investigationDate,
        String status,
        DailyPaymentReport dailyReport,
        List<PaymentAnomalySignal> anomalies,
        List<ChannelFailureCodeStat> channelFailureCodeStats,
        List<HourlyPaymentStat> hourlyStats,
        List<UserFailureStat> userFailureStats,
        List<AmountBucketStat> amountBucketStats,
        List<PaymentTransactionEvidence> transactionSamples,
        List<PaymentKnowledgeEvidence> knowledgeEvidence,
        List<String> investigationSteps,
        String conclusion) {
}
