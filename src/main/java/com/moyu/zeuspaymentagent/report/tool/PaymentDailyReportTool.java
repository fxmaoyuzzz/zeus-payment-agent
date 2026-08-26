package com.moyu.zeuspaymentagent.report.tool;

import com.moyu.zeuspaymentagent.audit.service.ToolCallAuditService;
import com.moyu.zeuspaymentagent.report.model.DailyPaymentReport;
import com.moyu.zeuspaymentagent.report.service.PaymentDailyReportService;
import java.time.LocalDate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentDailyReportTool {

    private final PaymentDailyReportService paymentDailyReportService;
    private final ToolCallAuditService toolCallAuditService;

    public PaymentDailyReportTool(
            PaymentDailyReportService paymentDailyReportService,
            ToolCallAuditService toolCallAuditService) {
        this.paymentDailyReportService = paymentDailyReportService;
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * Tool 流程：LLM 提取日报日期 -> 聚合支付数据 -> 返回结构化日报。
     */
    @Tool(
            name = "generate_payment_daily_report",
            description = "生成指定日期的支付日报。用户要求生成日报、支付日报、昨日支付概览时调用。")
    public DailyPaymentReport generatePaymentDailyReport(
            @ToolParam(required = false, description = "日报日期，格式 yyyy-MM-dd；为空时默认昨天")
                    String reportDate) {
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {reportDate};
        try {
            var result = paymentDailyReportService.generate(parseDate(reportDate));
            toolCallAuditService.record("generate_payment_daily_report", getClass().getName(),
                    "generatePaymentDailyReport", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("generate_payment_daily_report", getClass().getName(),
                    "generatePaymentDailyReport", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    private LocalDate parseDate(String reportDate) {
        if (!StringUtils.hasText(reportDate)) {
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.parse(reportDate);
    }
}
