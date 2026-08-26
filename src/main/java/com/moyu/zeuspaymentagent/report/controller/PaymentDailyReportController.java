package com.moyu.zeuspaymentagent.report.controller;

import com.moyu.zeuspaymentagent.report.model.DailyPaymentReport;
import com.moyu.zeuspaymentagent.report.service.PaymentDailyReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class PaymentDailyReportController {

    private final PaymentDailyReportService paymentDailyReportService;

    public PaymentDailyReportController(PaymentDailyReportService paymentDailyReportService) {
        this.paymentDailyReportService = paymentDailyReportService;
    }

    /**
     * 手动生成指定日期的支付日报，不传日期时默认生成昨天。
     */
    @PostMapping("/daily-payment")
    public DailyPaymentReport generateDailyPaymentReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate reportDate) {
        return paymentDailyReportService.generate(reportDate == null ? LocalDate.now().minusDays(1) : reportDate);
    }
}
