package com.moyu.zeuspaymentagent.payment.tool;

import com.moyu.zeuspaymentagent.audit.service.ToolCallAuditService;
import com.moyu.zeuspaymentagent.order.mapper.PaymentOrderMapper;
import com.moyu.zeuspaymentagent.order.model.PaymentOrderView;
import com.moyu.zeuspaymentagent.payment.mapper.PaymentFailureAnalysisMapper;
import com.moyu.zeuspaymentagent.payment.model.PaymentFailureAnalysisResult;
import com.moyu.zeuspaymentagent.payment.model.PaymentLog;
import com.moyu.zeuspaymentagent.payment.model.PaymentTransaction;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentFailureAnalysisTool {

    private static final int LOG_LIMIT = 30;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentFailureAnalysisMapper analysisMapper;
    private final ToolCallAuditService toolCallAuditService;

    public PaymentFailureAnalysisTool(
            PaymentOrderMapper paymentOrderMapper,
            PaymentFailureAnalysisMapper analysisMapper,
            ToolCallAuditService toolCallAuditService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.analysisMapper = analysisMapper;
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * 分析流程：查订单 -> 查最近支付流水 -> 查支付日志 -> 匹配失败规则 -> 组装证据和建议。
     */
    @Tool(
            name = "analyze_payment_failure",
            description = "根据订单号自动分析支付失败原因。用户询问为什么失败、失败原因、如何处理时优先调用这个工具。")
    public PaymentFailureAnalysisResult analyzePaymentFailure(
            @ToolParam(description = "支付订单号，例如 P202608250001") String orderNo) {
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {orderNo};
        try {
        var order = paymentOrderMapper.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            var result = new PaymentFailureAnalysisResult(
                    orderNo,
                    false,
                    null,
                    null,
                    null,
                    "UNKNOWN",
                    "没有查询到该订单，无法分析支付失败原因。",
                    "请确认订单号是否正确。",
                    "LOW",
                    List.of("订单表未查询到记录"),
                    List.of());
            toolCallAuditService.record("analyze_payment_failure", getClass().getName(),
                    "analyzePaymentFailure", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }

        var transaction = analysisMapper.findLatestTransactionByOrderNo(orderNo).orElse(null);
        var logs = analysisMapper.findLogsByOrderNoAndTransactionNo(
                orderNo, transaction == null ? null : transaction.getTransactionNo(), LOG_LIMIT);
        var rule = transaction == null ? null : analysisMapper.findBestRule(
                        transaction.getMethodCode(),
                        transaction.getChannelCode(),
                        transaction.getFailureCode(),
                        firstText(transaction.getChannelErrorCode(), latestLogChannelErrorCode(logs)))
                .orElse(null);

        var evidence = new ArrayList<String>();
        evidence.add("订单状态：" + order.getStatus());
        if (StringUtils.hasText(order.getFailureReason())) {
            evidence.add("订单失败原因：" + order.getFailureReason());
        }
        if (transaction != null) {
            evidence.add("最近支付流水：" + transaction.getTransactionNo());
            evidence.add("支付方式：" + transaction.getMethodCode());
            evidence.add("支付渠道：" + transaction.getChannelCode());
            evidence.add("支付状态：" + transaction.getStatus());
            addEvidence(evidence, "内部失败码", transaction.getFailureCode());
            addEvidence(evidence, "流水失败原因", transaction.getFailureReason());
            addEvidence(evidence, "渠道错误码", transaction.getChannelErrorCode());
            addEvidence(evidence, "渠道错误信息", transaction.getChannelErrorMessage());
        }
        else {
            evidence.add("未查询到支付流水");
        }
        appendLogEvidence(evidence, logs);

        var reasonType = "UNKNOWN";
        var reasonMessage = inferReasonMessage(order.getFailureReason(), transaction);
        var suggestion = "请结合订单状态、支付流水和支付日志继续排查。";
        var confidence = "LOW";

        if (rule != null) {
            reasonType = rule.getReasonType();
            reasonMessage = rule.getReasonMessage();
            suggestion = rule.getSuggestion();
            confidence = "HIGH";
            evidence.add("命中失败规则：" + rule.getReasonType() + "，优先级：" + rule.getPriority());
        }
        else if (transaction != null) {
            reasonType = inferReasonType(transaction, logs);
            suggestion = inferSuggestion(reasonType);
            confidence = "MEDIUM";
        }

        var result = new PaymentFailureAnalysisResult(
                orderNo,
                true,
                PaymentOrderView.from(order),
                transaction,
                rule,
                reasonType,
                reasonMessage,
                suggestion,
                confidence,
                evidence,
                logs);
            toolCallAuditService.record("analyze_payment_failure", getClass().getName(),
                    "analyzePaymentFailure", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("analyze_payment_failure", getClass().getName(),
                    "analyzePaymentFailure", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    private String inferReasonMessage(String orderFailureReason, PaymentTransaction transaction) {
        if (transaction != null && StringUtils.hasText(transaction.getFailureReason())) {
            return transaction.getFailureReason();
        }
        if (StringUtils.hasText(orderFailureReason)) {
            return orderFailureReason;
        }
        if (transaction == null) {
            return "订单没有关联支付流水，可能尚未发起支付或支付流水未落库。";
        }
        return "未命中明确失败规则，只能根据支付流水和日志做初步判断。";
    }

    private String inferReasonType(PaymentTransaction transaction, java.util.List<PaymentLog> logs) {
        var failureText = String.join(" ",
                nullToEmpty(transaction.getFailureCode()),
                nullToEmpty(transaction.getFailureReason()),
                nullToEmpty(transaction.getChannelErrorCode()),
                nullToEmpty(transaction.getChannelErrorMessage())).toUpperCase();

        if (failureText.contains("BALANCE") || failureText.contains("INSUFFICIENT")) {
            return "BALANCE";
        }
        if (failureText.contains("RISK") || failureText.contains("REJECT")) {
            return "RISK";
        }
        if (failureText.contains("TIMEOUT") || logs.stream().anyMatch(log -> "TIMEOUT".equalsIgnoreCase(log.getEventStatus()))) {
            return "NETWORK";
        }
        if (failureText.contains("CANCEL")) {
            return "USER";
        }
        if (failureText.contains("SYSTEM") || failureText.contains("ERROR")) {
            return "SYSTEM";
        }
        return "UNKNOWN";
    }

    private String inferSuggestion(String reasonType) {
        return switch (reasonType) {
            case "BALANCE" -> "建议用户确认余额或额度后重试，也可以更换支付方式。";
            case "RISK" -> "建议用户更换支付方式；如需继续排查，查看渠道风控返回和用户风险状态。";
            case "NETWORK" -> "建议检查渠道请求耗时、超时日志和渠道状态，必要时做补单或重试。";
            case "USER" -> "建议用户重新发起支付。";
            case "SYSTEM" -> "建议查看应用日志、Trace 和近期发布变更。";
            default -> "建议补充失败码规则，或继续查看支付日志和渠道返回。";
        };
    }

    private void appendLogEvidence(ArrayList<String> evidence, java.util.List<PaymentLog> logs) {
        if (logs.isEmpty()) {
            evidence.add("未查询到支付日志");
            return;
        }

        evidence.add("支付日志数量：" + logs.size());
        logs.stream()
                .filter(log -> !"SUCCESS".equalsIgnoreCase(log.getEventStatus()))
                .findFirst()
                .ifPresent(log -> evidence.add("首个异常日志：" + log.getEventType() + "/" + log.getEventStatus()));
    }

    private String latestLogChannelErrorCode(java.util.List<PaymentLog> logs) {
        for (var i = logs.size() - 1; i >= 0; i--) {
            var errorCode = logs.get(i).getChannelErrorCode();
            if (StringUtils.hasText(errorCode)) {
                return errorCode;
            }
        }
        return null;
    }

    private void addEvidence(ArrayList<String> evidence, String label, String value) {
        if (StringUtils.hasText(value)) {
            evidence.add(label + "：" + value);
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
