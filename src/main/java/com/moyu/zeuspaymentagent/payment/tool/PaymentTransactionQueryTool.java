package com.moyu.zeuspaymentagent.payment.tool;

import com.moyu.zeuspaymentagent.audit.service.ToolCallAuditService;
import com.moyu.zeuspaymentagent.payment.mapper.PaymentTransactionQueryMapper;
import com.moyu.zeuspaymentagent.payment.model.PaymentTransactionQueryResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentTransactionQueryTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PaymentTransactionQueryMapper paymentTransactionQueryMapper;
    private final ToolCallAuditService toolCallAuditService;

    public PaymentTransactionQueryTool(
            PaymentTransactionQueryMapper paymentTransactionQueryMapper,
            ToolCallAuditService toolCallAuditService) {
        this.paymentTransactionQueryMapper = paymentTransactionQueryMapper;
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * Tool 流程：LLM 提取流水查询条件 -> 查询 MySQL 支付流水 -> 返回结构化明细。
     */
    @Tool(
            name = "query_payment_transactions",
            description = "查询支付流水明细。用户询问支付流水、交易记录、支付单、某用户支付记录或某渠道流水时调用。")
    public PaymentTransactionQueryResult queryPaymentTransactions(
            @ToolParam(required = false, description = "支付流水号，例如 T202608250001") String transactionNo,
            @ToolParam(required = false, description = "业务订单号，例如 P202608250001") String orderNo,
            @ToolParam(required = false, description = "用户ID，例如 UTEST00022") String userId,
            @ToolParam(required = false, description = "支付状态：PENDING/SUCCESS/FAILED/CANCELLED/TIMEOUT") String status,
            @ToolParam(required = false, description = "支付渠道编码，例如 WECHAT_OFFICIAL") String channelCode,
            @ToolParam(required = false, description = "开始日期，格式 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "结束日期，格式 yyyy-MM-dd，包含当天") String endDate,
            @ToolParam(required = false, description = "最大返回数量，默认20，最大100") Integer limit) {
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {transactionNo, orderNo, userId, status, channelCode, startDate, endDate, limit};
        try {
            var startTime = parseStartTime(startDate);
            var endTime = parseEndTime(endDate);
            var normalizedLimit = normalizeLimit(limit);
            var transactions = paymentTransactionQueryMapper.queryTransactions(
                    blankToNull(transactionNo),
                    blankToNull(orderNo),
                    blankToNull(userId),
                    blankToNull(status),
                    blankToNull(channelCode),
                    startTime,
                    endTime,
                    normalizedLimit);

            var result = new PaymentTransactionQueryResult(
                    transactionNo,
                    orderNo,
                    userId,
                    status,
                    channelCode,
                    startDate,
                    endDate,
                    transactions.size(),
                    transactions);
            toolCallAuditService.record("query_payment_transactions", getClass().getName(),
                    "queryPaymentTransactions", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("query_payment_transactions", getClass().getName(),
                    "queryPaymentTransactions", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    private LocalDateTime parseStartTime(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date).atStartOfDay() : null;
    }

    private LocalDateTime parseEndTime(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date).plusDays(1).atStartOfDay() : null;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
