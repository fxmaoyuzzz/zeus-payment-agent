package com.moyu.zeuspaymentagent.investigation.controller;

import com.moyu.zeuspaymentagent.investigation.model.PaymentAnomalyInvestigationResult;
import com.moyu.zeuspaymentagent.investigation.service.PaymentAnomalyInvestigationService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/investigations")
public class PaymentAnomalyInvestigationController {

    private final PaymentAnomalyInvestigationService investigationService;

    public PaymentAnomalyInvestigationController(PaymentAnomalyInvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    /**
     * 手动触发指定日期的支付异常调查，不查询支付日志。
     */
    @PostMapping("/payment-anomaly")
    public PaymentAnomalyInvestigationResult investigatePaymentAnomaly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate investigationDate,
            @RequestParam(required = false) String question) {
        return investigationService.investigate(
                investigationDate == null ? LocalDate.now().minusDays(1) : investigationDate,
                question,
                "MANUAL");
    }
}
