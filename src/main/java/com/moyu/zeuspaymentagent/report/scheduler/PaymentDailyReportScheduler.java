package com.moyu.zeuspaymentagent.report.scheduler;

import com.moyu.zeuspaymentagent.report.service.PaymentDailyReportService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentDailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentDailyReportScheduler.class);

    private final PaymentDailyReportService paymentDailyReportService;

    public PaymentDailyReportScheduler(PaymentDailyReportService paymentDailyReportService) {
        this.paymentDailyReportService = paymentDailyReportService;
    }

    /**
     * 自动生成昨天的支付日报，默认每天 00:05 执行。
     */
    @Scheduled(cron = "${zeus.report.daily-cron}", zone = "${zeus.report.zone}")
    public void generateYesterdayReport() {
        var reportDate = LocalDate.now().minusDays(1);
        try {
            var report = paymentDailyReportService.generate(reportDate);
            log.info("Payment daily report generated, reportDate={}, totalOrders={}",
                    report.reportDate(), report.totalOrders());
        }
        catch (RuntimeException ex) {
            log.error("Failed to generate payment daily report, reportDate={}", reportDate, ex);
        }
    }
}
