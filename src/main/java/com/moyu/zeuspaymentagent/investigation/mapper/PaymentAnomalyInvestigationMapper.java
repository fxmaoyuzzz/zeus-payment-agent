package com.moyu.zeuspaymentagent.investigation.mapper;

import com.moyu.zeuspaymentagent.investigation.model.PaymentTransactionEvidence;
import com.moyu.zeuspaymentagent.investigation.model.AmountBucketStat;
import com.moyu.zeuspaymentagent.investigation.model.ChannelFailureCodeStat;
import com.moyu.zeuspaymentagent.investigation.model.HourlyPaymentStat;
import com.moyu.zeuspaymentagent.investigation.model.UserFailureStat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentAnomalyInvestigationMapper {

    /**
     * 查询指定日期内失败、取消或超时的支付流水样本。
     */
    @Select("""
            SELECT
                transaction_no AS transactionNo,
                order_no AS orderNo,
                user_id AS userId,
                method_code AS methodCode,
                channel_code AS channelCode,
                amount,
                currency,
                status,
                failure_code AS failureCode,
                failure_reason AS failureReason,
                channel_error_code AS channelErrorCode,
                channel_error_message AS channelErrorMessage,
                created_at AS createdAt
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
              AND status IN ('FAILED', 'CANCELLED', 'TIMEOUT')
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<PaymentTransactionEvidence> listFailedTransactionSamples(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 按渠道和失败码交叉统计，定位异常集中在哪个渠道和错误类型。
     */
    @Select("""
            SELECT
                method_code AS methodCode,
                channel_code AS channelCode,
                failure_code AS failureCode,
                channel_error_code AS channelErrorCode,
                failure_reason AS failureReason,
                COUNT(*) AS failureCount
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
              AND status IN ('FAILED', 'CANCELLED', 'TIMEOUT')
            GROUP BY method_code, channel_code, failure_code, channel_error_code, failure_reason
            ORDER BY failureCount DESC
            LIMIT #{limit}
            """)
    List<ChannelFailureCodeStat> summarizeChannelFailureCodes(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 按小时统计支付失败分布，判断异常是否集中爆发。
     */
    @Select("""
            SELECT
                HOUR(created_at) AS hourOfDay,
                COUNT(*) AS totalCount,
                COALESCE(SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END), 0) AS failedCount,
                COALESCE(
                    SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END) / COUNT(*),
                    0
                ) AS failureRate
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            GROUP BY HOUR(created_at)
            ORDER BY hourOfDay ASC
            """)
    List<HourlyPaymentStat> summarizeHourlyPayments(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按用户统计失败集中度，识别是否集中影响少量用户。
     */
    @Select("""
            SELECT
                user_id AS userId,
                COUNT(*) AS totalCount,
                COALESCE(SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END), 0) AS failedCount,
                COALESCE(SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN amount ELSE 0 END), 0) AS failureAmount
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            GROUP BY user_id
            HAVING failedCount > 0
            ORDER BY failedCount DESC, failureAmount DESC
            LIMIT #{limit}
            """)
    List<UserFailureStat> summarizeUserFailures(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    /**
     * 按金额区间统计失败分布，识别是否集中在某个金额段。
     */
    @Select("""
            SELECT
                CASE
                    WHEN amount < 50 THEN '0-50'
                    WHEN amount < 200 THEN '50-200'
                    WHEN amount < 500 THEN '200-500'
                    WHEN amount < 1000 THEN '500-1000'
                    ELSE '1000+'
                END AS amountBucket,
                COUNT(*) AS totalCount,
                COALESCE(SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END), 0) AS failedCount,
                COALESCE(
                    SUM(CASE WHEN status IN ('FAILED', 'CANCELLED', 'TIMEOUT') THEN 1 ELSE 0 END) / COUNT(*),
                    0
                ) AS failureRate
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
            GROUP BY amountBucket
            ORDER BY failedCount DESC, amountBucket ASC
            """)
    List<AmountBucketStat> summarizeAmountBuckets(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 根据异常维度继续查询支付流水样本。
     */
    @Select("""
            SELECT
                transaction_no AS transactionNo,
                order_no AS orderNo,
                user_id AS userId,
                method_code AS methodCode,
                channel_code AS channelCode,
                amount,
                currency,
                status,
                failure_code AS failureCode,
                failure_reason AS failureReason,
                channel_error_code AS channelErrorCode,
                channel_error_message AS channelErrorMessage,
                created_at AS createdAt
            FROM payment_transaction
            WHERE created_at >= #{startTime}
              AND created_at < #{endTime}
              AND status IN ('FAILED', 'CANCELLED', 'TIMEOUT')
              AND (#{channelCode} IS NULL OR channel_code = #{channelCode})
              AND (#{failureCode} IS NULL OR failure_code = #{failureCode})
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<PaymentTransactionEvidence> listFocusedTransactionSamples(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("channelCode") String channelCode,
            @Param("failureCode") String failureCode,
            @Param("limit") int limit);

    /**
     * 保存或更新异常事件，便于后续复盘和审计。
     */
    @Insert("""
            INSERT INTO payment_anomaly_event
            (anomaly_no, anomaly_date, anomaly_type, severity, status, title, description,
             metric_name, metric_value, threshold_value, dimension_type, dimension_value, created_at, updated_at)
            VALUES
            (#{anomalyNo}, #{anomalyDate}, #{anomalyType}, #{severity}, 'NEW', #{title}, #{description},
             #{metricName}, #{metricValue}, #{thresholdValue}, #{dimensionType}, #{dimensionValue}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                anomaly_type = VALUES(anomaly_type),
                severity = VALUES(severity),
                title = VALUES(title),
                description = VALUES(description),
                metric_name = VALUES(metric_name),
                metric_value = VALUES(metric_value),
                threshold_value = VALUES(threshold_value),
                dimension_type = VALUES(dimension_type),
                dimension_value = VALUES(dimension_value),
                updated_at = VALUES(updated_at)
            """)
    int upsertAnomalyEvent(
            @Param("anomalyNo") String anomalyNo,
            @Param("anomalyDate") LocalDate anomalyDate,
            @Param("anomalyType") String anomalyType,
            @Param("severity") String severity,
            @Param("title") String title,
            @Param("description") String description,
            @Param("metricName") String metricName,
            @Param("metricValue") BigDecimal metricValue,
            @Param("thresholdValue") BigDecimal thresholdValue,
            @Param("dimensionType") String dimensionType,
            @Param("dimensionValue") String dimensionValue);

    /**
     * 创建调查任务。
     */
    @Insert("""
            INSERT INTO payment_investigation
            (investigation_no, anomaly_no, investigation_date, trigger_type, status, question,
             started_at, created_at, updated_at)
            VALUES
            (#{investigationNo}, #{anomalyNo}, #{investigationDate}, #{triggerType}, 'RUNNING', #{question},
             #{startedAt}, NOW(), NOW())
            """)
    int insertInvestigation(
            @Param("investigationNo") String investigationNo,
            @Param("anomalyNo") String anomalyNo,
            @Param("investigationDate") LocalDate investigationDate,
            @Param("triggerType") String triggerType,
            @Param("question") String question,
            @Param("startedAt") LocalDateTime startedAt);

    /**
     * 完成调查任务并保存最终结论。
     */
    @Update("""
            UPDATE payment_investigation
            SET status = #{status},
                summary = #{summary},
                conclusion = #{conclusion},
                finished_at = #{finishedAt},
                updated_at = NOW()
            WHERE investigation_no = #{investigationNo}
            """)
    int completeInvestigation(
            @Param("investigationNo") String investigationNo,
            @Param("status") String status,
            @Param("summary") String summary,
            @Param("conclusion") String conclusion,
            @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 保存调查步骤输入和输出。
     */
    @Insert("""
            INSERT INTO payment_investigation_step
            (investigation_no, step_no, step_type, step_name, status, input_content, output_content,
             started_at, finished_at, created_at, updated_at)
            VALUES
            (#{investigationNo}, #{stepNo}, #{stepType}, #{stepName}, 'COMPLETED', #{inputContent}, #{outputContent},
             #{startedAt}, #{finishedAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                input_content = VALUES(input_content),
                output_content = VALUES(output_content),
                started_at = VALUES(started_at),
                finished_at = VALUES(finished_at),
                updated_at = VALUES(updated_at)
            """)
    int upsertInvestigationStep(
            @Param("investigationNo") String investigationNo,
            @Param("stepNo") int stepNo,
            @Param("stepType") String stepType,
            @Param("stepName") String stepName,
            @Param("inputContent") String inputContent,
            @Param("outputContent") String outputContent,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 保存调查证据，包含日报指标、流水样本和知识库片段。
     */
    @Insert("""
            INSERT INTO payment_investigation_evidence
            (investigation_no, step_no, evidence_type, evidence_source, reference_id, title,
             content, confidence, created_at)
            VALUES
            (#{investigationNo}, #{stepNo}, #{evidenceType}, #{evidenceSource}, #{referenceId}, #{title},
             #{content}, #{confidence}, NOW())
            """)
    int insertEvidence(
            @Param("investigationNo") String investigationNo,
            @Param("stepNo") Integer stepNo,
            @Param("evidenceType") String evidenceType,
            @Param("evidenceSource") String evidenceSource,
            @Param("referenceId") String referenceId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("confidence") BigDecimal confidence);
}
