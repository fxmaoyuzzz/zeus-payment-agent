package com.moyu.zeuspaymentagent.investigation.tool;

import com.moyu.zeuspaymentagent.investigation.model.PaymentAnomalyInvestigationResult;
import com.moyu.zeuspaymentagent.investigation.service.PaymentAnomalyInvestigationService;
import java.time.LocalDate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentAnomalyInvestigationTool {

    private final PaymentAnomalyInvestigationService investigationService;

    public PaymentAnomalyInvestigationTool(PaymentAnomalyInvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    /**
     * Tool 流程：LLM 提取调查日期和问题 -> 自动执行异常识别、流水查询、知识库检索和结论汇总。
     */
    @Tool(
            name = "investigate_payment_anomaly",
            description = "调查指定日期的支付异常，不查询支付日志。用户要求调查支付异常、继续排查、看是否有异常订单或异常流水时调用。")
    public PaymentAnomalyInvestigationResult investigatePaymentAnomaly(
            @ToolParam(required = false, description = "调查日期，格式 yyyy-MM-dd；为空时默认昨天")
                    String investigationDate,
            @ToolParam(required = false, description = "用户原始调查问题")
                    String question) {
        return investigationService.investigate(parseDate(investigationDate), question, "LLM_TOOL");
    }

    private LocalDate parseDate(String investigationDate) {
        if (!StringUtils.hasText(investigationDate)) {
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.parse(investigationDate);
    }
}
