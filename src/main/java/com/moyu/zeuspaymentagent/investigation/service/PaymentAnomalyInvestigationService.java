package com.moyu.zeuspaymentagent.investigation.service;

import com.moyu.zeuspaymentagent.investigation.mapper.PaymentAnomalyInvestigationMapper;
import com.moyu.zeuspaymentagent.investigation.model.AmountBucketStat;
import com.moyu.zeuspaymentagent.investigation.model.ChannelFailureCodeStat;
import com.moyu.zeuspaymentagent.investigation.model.HourlyPaymentStat;
import com.moyu.zeuspaymentagent.investigation.model.PaymentAnomalyInvestigationResult;
import com.moyu.zeuspaymentagent.investigation.model.PaymentAnomalySignal;
import com.moyu.zeuspaymentagent.investigation.model.PaymentKnowledgeEvidence;
import com.moyu.zeuspaymentagent.investigation.model.PaymentTransactionEvidence;
import com.moyu.zeuspaymentagent.investigation.model.UserFailureStat;
import com.moyu.zeuspaymentagent.knowledge.tool.KnowledgeSearchTool;
import com.moyu.zeuspaymentagent.report.model.DailyPaymentReport;
import com.moyu.zeuspaymentagent.report.model.PaymentChannelDailyStat;
import com.moyu.zeuspaymentagent.report.model.PaymentFailureDailyStat;
import com.moyu.zeuspaymentagent.report.service.PaymentDailyReportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PaymentAnomalyInvestigationService {

    private static final int TRANSACTION_SAMPLE_LIMIT = 20;
    private static final int STAT_TOP_LIMIT = 10;
    private static final BigDecimal DAILY_FAILURE_RATE_THRESHOLD = new BigDecimal("0.2000");
    private static final BigDecimal CHANNEL_FAILURE_RATE_THRESHOLD = new BigDecimal("0.4000");
    private static final long FAILURE_CODE_COUNT_THRESHOLD = 3;

    private final PaymentDailyReportService paymentDailyReportService;
    private final PaymentAnomalyInvestigationMapper investigationMapper;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final JsonMapper jsonMapper;

    public PaymentAnomalyInvestigationService(
            PaymentDailyReportService paymentDailyReportService,
            PaymentAnomalyInvestigationMapper investigationMapper,
            KnowledgeSearchTool knowledgeSearchTool,
            JsonMapper jsonMapper) {
        this.paymentDailyReportService = paymentDailyReportService;
        this.investigationMapper = investigationMapper;
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 异常调查流程：生成日报 -> 识别异常信号 -> 查询异常流水样本 -> 检索知识库 -> 保存调查记录。
     */
    @Transactional
    public PaymentAnomalyInvestigationResult investigate(LocalDate investigationDate, String question, String triggerType) {
        var startedAt = LocalDateTime.now();
        var investigationNo = buildInvestigationNo(investigationDate, startedAt);
        var report = paymentDailyReportService.summarizeWithoutLatency(investigationDate);
        var anomalies = detectAnomalies(investigationDate, report);
        var primaryAnomalyNo = anomalies.isEmpty() ? null : anomalies.get(0).anomalyNo();

        investigationMapper.insertInvestigation(
                investigationNo,
                primaryAnomalyNo,
                investigationDate,
                triggerType,
                question,
                startedAt);

        saveStep(investigationNo, 1, "DETECT_ANOMALY", "识别支付异常", investigationDate.toString(), anomalies);
        saveAnomalies(investigationDate, anomalies);
        saveEvidence(investigationNo, 1, "DAILY_REPORT", "MySQL", investigationDate.toString(), "支付日报指标", report, null);

        var channelFailureCodeStats = investigationMapper.summarizeChannelFailureCodes(
                report.startTime(),
                report.endTime(),
                STAT_TOP_LIMIT);
        var hourlyStats = investigationMapper.summarizeHourlyPayments(report.startTime(), report.endTime());
        var userFailureStats = investigationMapper.summarizeUserFailures(report.startTime(), report.endTime(), STAT_TOP_LIMIT);
        var amountBucketStats = investigationMapper.summarizeAmountBuckets(report.startTime(), report.endTime());
        var drillDownStats = new DrillDownStats(
                channelFailureCodeStats,
                hourlyStats,
                userFailureStats,
                amountBucketStats);
        saveStep(investigationNo, 2, "DRILL_DOWN", "按渠道、失败码、小时、用户和金额区间继续分析", investigationDate.toString(), drillDownStats);
        saveEvidence(investigationNo, 2, "METRIC", "MySQL", investigationDate.toString(), "异常维度深挖指标", drillDownStats, null);

        var focus = chooseInvestigationFocus(anomalies, channelFailureCodeStats);
        var samples = investigationMapper.listFocusedTransactionSamples(
                report.startTime(),
                report.endTime(),
                focus.channelCode(),
                focus.failureCode(),
                TRANSACTION_SAMPLE_LIMIT);
        if (samples.isEmpty()) {
            samples = investigationMapper.listFailedTransactionSamples(
                    report.startTime(),
                    report.endTime(),
                    TRANSACTION_SAMPLE_LIMIT);
        }
        saveStep(investigationNo, 3, "QUERY_TRANSACTION", "按异常维度查询支付流水样本", toJson(focus), samples);
        for (var sample : samples) {
            saveEvidence(investigationNo, 3, "TRANSACTION", "MySQL", sample.transactionNo(), sample.orderNo(), sample, null);
        }

        var knowledgeEvidence = searchKnowledgeEvidence(report.failureStats());
        saveStep(investigationNo, 4, "SEARCH_KNOWLEDGE", "检索支付知识库建议", buildKnowledgeQuery(report.failureStats()), knowledgeEvidence);
        for (var evidence : knowledgeEvidence) {
            saveEvidence(
                    investigationNo,
                    4,
                    "KNOWLEDGE",
                    "Chroma",
                    evidence.source() + "#" + evidence.chunkIndex(),
                    evidence.title(),
                    evidence,
                    scoreToConfidence(evidence.score()));
        }

        var steps = buildInvestigationSteps(focus);
        var conclusion = buildConclusion(
                report,
                anomalies,
                channelFailureCodeStats,
                hourlyStats,
                userFailureStats,
                amountBucketStats,
                samples,
                knowledgeEvidence,
                steps);
        saveStep(investigationNo, 5, "SUMMARIZE", "汇总调查结论", investigationDate.toString(), conclusion);
        investigationMapper.completeInvestigation(
                investigationNo,
                "COMPLETED",
                summary(conclusion),
                conclusion,
                LocalDateTime.now());

        return new PaymentAnomalyInvestigationResult(
                investigationNo,
                investigationDate,
                "COMPLETED",
                report,
                anomalies,
                channelFailureCodeStats,
                hourlyStats,
                userFailureStats,
                amountBucketStats,
                samples,
                knowledgeEvidence,
                steps,
                conclusion);
    }

    private List<PaymentAnomalySignal> detectAnomalies(LocalDate investigationDate, DailyPaymentReport report) {
        var anomalies = new ArrayList<PaymentAnomalySignal>();
        if (report.failureRate().compareTo(DAILY_FAILURE_RATE_THRESHOLD) >= 0) {
            anomalies.add(new PaymentAnomalySignal(
                    buildAnomalyNo(investigationDate, "DAILY_FAILURE_RATE", anomalies.size() + 1),
                    "HIGH_FAILURE_RATE",
                    severity(report.failureRate(), new BigDecimal("0.5000")),
                    "支付失败率超过阈值",
                    "当日整体支付失败率达到 " + percent(report.failureRate()) + "，超过阈值 "
                            + percent(DAILY_FAILURE_RATE_THRESHOLD) + "。",
                    "failure_rate",
                    report.failureRate(),
                    DAILY_FAILURE_RATE_THRESHOLD,
                    "DATE",
                    investigationDate.toString()));
        }

        detectChannelAnomalies(investigationDate, report.channelStats(), anomalies);
        detectFailureCodeAnomalies(investigationDate, report.failureStats(), anomalies);
        return anomalies;
    }

    private void detectChannelAnomalies(
            LocalDate investigationDate,
            List<PaymentChannelDailyStat> channelStats,
            List<PaymentAnomalySignal> anomalies) {
        for (var stat : channelStats) {
            var total = number(stat.getTotalCount());
            var failed = number(stat.getFailedCount());
            if (total == 0 || failed < FAILURE_CODE_COUNT_THRESHOLD) {
                continue;
            }

            var failureRate = rate(failed, total);
            if (failureRate.compareTo(CHANNEL_FAILURE_RATE_THRESHOLD) < 0) {
                continue;
            }

            anomalies.add(new PaymentAnomalySignal(
                    buildAnomalyNo(investigationDate, "CHANNEL_FAILURE_SPIKE", anomalies.size() + 1),
                    "CHANNEL_FAILURE_SPIKE",
                    severity(failureRate, new BigDecimal("0.6000")),
                    "支付渠道失败集中",
                    stat.getChannelCode() + " 失败率达到 " + percent(failureRate) + "，失败 "
                            + failed + " 笔。",
                    "channel_failure_rate",
                    failureRate,
                    CHANNEL_FAILURE_RATE_THRESHOLD,
                    "CHANNEL",
                    stat.getChannelCode()));
        }
    }

    private void detectFailureCodeAnomalies(
            LocalDate investigationDate,
            List<PaymentFailureDailyStat> failureStats,
            List<PaymentAnomalySignal> anomalies) {
        for (var stat : failureStats) {
            var failureCount = number(stat.getFailureCount());
            if (failureCount < FAILURE_CODE_COUNT_THRESHOLD) {
                continue;
            }

            anomalies.add(new PaymentAnomalySignal(
                    buildAnomalyNo(investigationDate, "FAILURE_CODE_SPIKE", anomalies.size() + 1),
                    "FAILURE_CODE_SPIKE",
                    failureCount >= 10 ? "HIGH" : "MEDIUM",
                    "失败码集中出现",
                    safeText(stat.getFailureCode()) + " 出现 " + failureCount + " 次，建议优先排查。",
                    "failure_code_count",
                    BigDecimal.valueOf(failureCount),
                    BigDecimal.valueOf(FAILURE_CODE_COUNT_THRESHOLD),
                    "FAILURE_CODE",
                    safeText(stat.getFailureCode())));
        }
    }

    private List<PaymentKnowledgeEvidence> searchKnowledgeEvidence(List<PaymentFailureDailyStat> failureStats) {
        return failureStats.stream()
                .sorted(Comparator.comparing((PaymentFailureDailyStat stat) -> number(stat.getFailureCount())).reversed())
                .limit(3)
                .flatMap(stat -> knowledgeSearchTool.searchKnowledgeBase(buildKnowledgeQuery(stat), 2).hits().stream())
                .map(hit -> new PaymentKnowledgeEvidence(
                        hit.source(),
                        hit.title(),
                        hit.chunkIndex(),
                        hit.score(),
                        hit.content()))
                .limit(5)
                .toList();
    }

    private InvestigationFocus chooseInvestigationFocus(
            List<PaymentAnomalySignal> anomalies,
            List<ChannelFailureCodeStat> channelFailureCodeStats) {
        var channelCode = anomalies.stream()
                .filter(anomaly -> "CHANNEL".equals(anomaly.dimensionType()))
                .map(PaymentAnomalySignal::dimensionValue)
                .findFirst()
                .orElse(null);
        var failureCode = anomalies.stream()
                .filter(anomaly -> "FAILURE_CODE".equals(anomaly.dimensionType()))
                .map(PaymentAnomalySignal::dimensionValue)
                .filter(value -> !"UNKNOWN".equals(value))
                .findFirst()
                .orElse(null);

        if ((channelCode == null || failureCode == null) && !channelFailureCodeStats.isEmpty()) {
            var topStat = channelFailureCodeStats.get(0);
            channelCode = channelCode == null ? topStat.getChannelCode() : channelCode;
            failureCode = failureCode == null ? topStat.getFailureCode() : failureCode;
        }

        return new InvestigationFocus(channelCode, failureCode);
    }

    private List<String> buildInvestigationSteps(InvestigationFocus focus) {
        var steps = new ArrayList<String>();
        steps.add("识别整体失败率、渠道失败率和失败码集中度");
        steps.add("按渠道、失败码、小时、用户和金额区间继续深挖");
        if (focus.channelCode() != null || focus.failureCode() != null) {
            steps.add("聚焦查询 " + safeText(focus.channelCode()) + " / " + safeText(focus.failureCode()) + " 的异常流水样本");
        }
        else {
            steps.add("未命中特定异常维度，查询当日失败流水样本");
        }
        steps.add("根据 Top 失败码检索支付知识库处理建议");
        steps.add("汇总异常模式、影响范围和下一步动作");
        return steps;
    }

    private String buildConclusion(
            DailyPaymentReport report,
            List<PaymentAnomalySignal> anomalies,
            List<ChannelFailureCodeStat> channelFailureCodeStats,
            List<HourlyPaymentStat> hourlyStats,
            List<UserFailureStat> userFailureStats,
            List<AmountBucketStat> amountBucketStats,
            List<PaymentTransactionEvidence> samples,
            List<PaymentKnowledgeEvidence> knowledgeEvidence,
            List<String> steps) {
        var builder = new StringBuilder();
        builder.append("支付异常调查结论\n\n");
        builder.append("调查日期：").append(report.reportDate()).append("\n");
        builder.append("订单总数：").append(report.totalOrders())
                .append("，成功率：").append(percent(report.successRate()))
                .append("，失败率：").append(percent(report.failureRate())).append("\n\n");

        builder.append("调查路径：\n");
        for (var index = 0; index < steps.size(); index++) {
            builder.append(index + 1).append(". ").append(steps.get(index)).append("\n");
        }

        if (anomalies.isEmpty()) {
            builder.append("\n未发现超过当前阈值的明显异常，但仍可结合业务活动和渠道公告继续观察。\n");
        }
        else {
            builder.append("\n发现异常：\n");
            for (var anomaly : anomalies) {
                builder.append("- ").append(anomaly.title())
                        .append("：").append(anomaly.description())
                        .append(" 严重级别：").append(anomaly.severity()).append("\n");
            }
        }

        appendTopChannelFailureCode(builder, channelFailureCodeStats);
        appendTopHourlyWindow(builder, hourlyStats);
        appendTopUserFailure(builder, userFailureStats);
        appendTopAmountBucket(builder, amountBucketStats);

        if (!samples.isEmpty()) {
            builder.append("\n异常流水样本：\n");
            samples.stream().limit(5).forEach(sample -> builder.append("- ")
                    .append(sample.transactionNo()).append(" / ")
                    .append(sample.orderNo()).append(" / ")
                    .append(sample.channelCode()).append(" / ")
                    .append(sample.status()).append(" / ")
                    .append(safeText(sample.failureCode())).append("\n"));
        }

        if (!knowledgeEvidence.isEmpty()) {
            builder.append("\n知识库建议：\n");
            knowledgeEvidence.stream().limit(3).forEach(evidence -> builder.append("- ")
                    .append(safeText(evidence.title()))
                    .append("，来源：").append(safeText(evidence.source())).append("\n"));
        }

        builder.append("\n建议动作：优先处理高失败率渠道和 Top 失败码，确认渠道状态、风控策略、用户侧余额或账户状态，并持续观察后续交易成功率。");
        return builder.toString();
    }

    private void appendTopChannelFailureCode(StringBuilder builder, List<ChannelFailureCodeStat> stats) {
        if (stats.isEmpty()) {
            return;
        }
        var stat = stats.get(0);
        builder.append("\n渠道 + 失败码交叉分析：\n")
                .append("- 最集中组合是 ")
                .append(safeText(stat.getChannelCode()))
                .append(" / ")
                .append(safeText(stat.getFailureCode()))
                .append("，出现 ")
                .append(number(stat.getFailureCount()))
                .append(" 次。原因：")
                .append(safeText(stat.getFailureReason()))
                .append("\n");
    }

    private void appendTopHourlyWindow(StringBuilder builder, List<HourlyPaymentStat> stats) {
        stats.stream()
                .filter(stat -> number(stat.getFailedCount()) > 0)
                .max(Comparator.comparing((HourlyPaymentStat stat) -> number(stat.getFailedCount()))
                        .thenComparing(stat -> normalizeRate(stat.getFailureRate())))
                .ifPresent(stat -> builder.append("\n小时窗口分析：\n")
                        .append("- 失败最集中的时间段是 ")
                        .append(stat.getHourOfDay())
                        .append(":00-")
                        .append(stat.getHourOfDay() + 1)
                        .append(":00，失败 ")
                        .append(number(stat.getFailedCount()))
                        .append(" 笔，失败率 ")
                        .append(percent(normalizeRate(stat.getFailureRate())))
                        .append("。\n"));
    }

    private void appendTopUserFailure(StringBuilder builder, List<UserFailureStat> stats) {
        if (stats.isEmpty()) {
            return;
        }
        var stat = stats.get(0);
        builder.append("\n用户集中度分析：\n")
                .append("- 失败最多用户是 ")
                .append(safeText(stat.getUserId()))
                .append("，失败 ")
                .append(number(stat.getFailedCount()))
                .append(" 笔，失败金额 ")
                .append(money(stat.getFailureAmount()))
                .append("。\n");
    }

    private void appendTopAmountBucket(StringBuilder builder, List<AmountBucketStat> stats) {
        stats.stream()
                .filter(stat -> number(stat.getFailedCount()) > 0)
                .findFirst()
                .ifPresent(stat -> builder.append("\n金额区间分析：\n")
                        .append("- 失败最多的金额区间是 ")
                        .append(safeText(stat.getAmountBucket()))
                        .append("，失败 ")
                        .append(number(stat.getFailedCount()))
                        .append(" 笔，失败率 ")
                        .append(percent(normalizeRate(stat.getFailureRate())))
                        .append("。\n"));
    }

    private void saveAnomalies(LocalDate investigationDate, List<PaymentAnomalySignal> anomalies) {
        for (var anomaly : anomalies) {
            investigationMapper.upsertAnomalyEvent(
                    anomaly.anomalyNo(),
                    investigationDate,
                    anomaly.anomalyType(),
                    anomaly.severity(),
                    anomaly.title(),
                    anomaly.description(),
                    anomaly.metricName(),
                    anomaly.metricValue(),
                    anomaly.thresholdValue(),
                    anomaly.dimensionType(),
                    anomaly.dimensionValue());
        }
    }

    private void saveStep(
            String investigationNo,
            int stepNo,
            String stepType,
            String stepName,
            String inputContent,
            Object outputContent) {
        var now = LocalDateTime.now();
        investigationMapper.upsertInvestigationStep(
                investigationNo,
                stepNo,
                stepType,
                stepName,
                inputContent,
                toJson(outputContent),
                now,
                now);
    }

    private void saveEvidence(
            String investigationNo,
            Integer stepNo,
            String evidenceType,
            String evidenceSource,
            String referenceId,
            String title,
            Object content,
            BigDecimal confidence) {
        investigationMapper.insertEvidence(
                investigationNo,
                stepNo,
                evidenceType,
                evidenceSource,
                referenceId,
                title,
                toJson(content),
                confidence);
    }

    private String buildInvestigationNo(LocalDate date, LocalDateTime time) {
        return "INV" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + time.format(DateTimeFormatter.ofPattern("HHmmssSSS"));
    }

    private String buildAnomalyNo(LocalDate date, String type, int index) {
        return "ANOM" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + type + String.format("%02d", index);
    }

    private String buildKnowledgeQuery(List<PaymentFailureDailyStat> failureStats) {
        return failureStats.stream()
                .findFirst()
                .map(this::buildKnowledgeQuery)
                .orElse("支付异常 失败率 支付失败处理 SOP");
    }

    private String buildKnowledgeQuery(PaymentFailureDailyStat stat) {
        return safeText(stat.getFailureCode()) + " " + safeText(stat.getChannelErrorCode()) + " "
                + safeText(stat.getFailureReason()) + " 支付失败处理 SOP";
    }

    private BigDecimal scoreToConfidence(Double score) {
        if (score == null) {
            return null;
        }
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private String severity(BigDecimal value, BigDecimal highThreshold) {
        return value.compareTo(highThreshold) >= 0 ? "HIGH" : "MEDIUM";
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String summary(String conclusion) {
        if (conclusion.length() <= 512) {
            return conclusion;
        }
        return conclusion.substring(0, 512);
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        }
        catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize investigation content", ex);
        }
    }

    private record InvestigationFocus(String channelCode, String failureCode) {
    }

    private record DrillDownStats(
            List<ChannelFailureCodeStat> channelFailureCodeStats,
            List<HourlyPaymentStat> hourlyStats,
            List<UserFailureStat> userFailureStats,
            List<AmountBucketStat> amountBucketStats) {
    }
}
