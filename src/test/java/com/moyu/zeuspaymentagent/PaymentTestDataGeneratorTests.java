package com.moyu.zeuspaymentagent;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class PaymentTestDataGeneratorTests {

    private static final int DATA_SIZE = 500;

    private static final List<PaymentRoute> ROUTES = List.of(
            new PaymentRoute("BANK_CARD", "BANK_ABC"),
            new PaymentRoute("BANK_CARD", "BANK_ICBC"),
            new PaymentRoute("WECHAT_PAY", "WECHAT_OFFICIAL"),
            new PaymentRoute("ALIPAY", "ALIPAY_OFFICIAL"),
            new PaymentRoute("PAYPAL", "PAYPAL_OFFICIAL"),
            new PaymentRoute("BALANCE", "BALANCE_INTERNAL"));

    /**
     * 手动造数流程：读取本地 MySQL 配置 -> 循环生成订单和支付流水 -> 批量写入数据库。
     */
    @Test
    @EnabledIfSystemProperty(named = "generateTestData", matches = "true")
    void insertPaymentTestDataIntoLocalMysql() throws Exception {
        var properties = loadLocalProperties();
        try (var connection = DriverManager.getConnection(
                properties.getProperty("MYSQL_URL"),
                properties.getProperty("MYSQL_USERNAME"),
                properties.getProperty("MYSQL_PASSWORD"))) {
            connection.setAutoCommit(false);
            insertMethods(connection);
            insertChannels(connection);
            insertOrdersAndTransactions(connection);
            connection.commit();
        }
    }

    private Properties loadLocalProperties() throws Exception {
        var path = Path.of("application-local.properties");
        if (!Files.exists(path)) {
            throw new IllegalStateException("application-local.properties does not exist in project root.");
        }

        var properties = new Properties();
        try (var inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private void insertMethods(Connection connection) throws Exception {
        var sql = """
                INSERT INTO payment_method (method_code, method_name, enabled, created_at, updated_at)
                VALUES (?, ?, 1, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    method_name = VALUES(method_name),
                    enabled = VALUES(enabled),
                    updated_at = VALUES(updated_at)
                """;

        try (var statement = connection.prepareStatement(sql)) {
            addMethod(statement, "BANK_CARD", "银行卡支付");
            addMethod(statement, "WECHAT_PAY", "微信支付");
            addMethod(statement, "ALIPAY", "支付宝");
            addMethod(statement, "PAYPAL", "PayPal");
            addMethod(statement, "BALANCE", "余额支付");
            statement.executeBatch();
        }
    }

    private void addMethod(PreparedStatement statement, String code, String name) throws Exception {
        statement.setString(1, code);
        statement.setString(2, name);
        statement.addBatch();
    }

    private void insertChannels(Connection connection) throws Exception {
        var sql = """
                INSERT INTO payment_channel (channel_code, channel_name, method_code, enabled, priority, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    channel_name = VALUES(channel_name),
                    method_code = VALUES(method_code),
                    enabled = VALUES(enabled),
                    priority = VALUES(priority),
                    updated_at = VALUES(updated_at)
                """;

        try (var statement = connection.prepareStatement(sql)) {
            addChannel(statement, "BANK_ABC", "农业银行通道", "BANK_CARD", 10);
            addChannel(statement, "BANK_ICBC", "工商银行通道", "BANK_CARD", 20);
            addChannel(statement, "WECHAT_OFFICIAL", "微信官方支付", "WECHAT_PAY", 10);
            addChannel(statement, "ALIPAY_OFFICIAL", "支付宝官方支付", "ALIPAY", 10);
            addChannel(statement, "PAYPAL_OFFICIAL", "PayPal 官方支付", "PAYPAL", 10);
            addChannel(statement, "BALANCE_INTERNAL", "站内余额支付", "BALANCE", 10);
            statement.executeBatch();
        }
    }

    private void addChannel(
            PreparedStatement statement, String code, String name, String methodCode, int priority) throws Exception {
        statement.setString(1, code);
        statement.setString(2, name);
        statement.setString(3, methodCode);
        statement.setInt(4, priority);
        statement.addBatch();
    }

    private void insertOrdersAndTransactions(Connection connection) throws Exception {
        var random = new Random(20260826);
        try (var orderStatement = connection.prepareStatement(orderSql());
                var transactionStatement = connection.prepareStatement(transactionSql())) {
            for (var i = 1; i <= DATA_SIZE; i++) {
                var route = ROUTES.get(random.nextInt(ROUTES.size()));
                var createdAt = LocalDate.of(2026, 8, 20)
                        .plusDays(random.nextInt(7))
                        .atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60));
                var status = nextOrderStatus(random);
                var paymentResult = buildPaymentResult(route, status, random);
                var orderNo = "PTEST202608" + String.format("%06d", i);
                var transactionNo = "TTEST202608" + String.format("%06d", i);
                var userId = "UTEST" + String.format("%05d", 1 + random.nextInt(120));
                var amount = BigDecimal.valueOf(1000 + random.nextInt(99000), 2);

                addOrder(orderStatement, orderNo, userId, amount, status, route.channelCode(), paymentResult.reason(), createdAt);
                addTransaction(transactionStatement, transactionNo, orderNo, userId, route, amount, paymentResult, createdAt);
            }

            orderStatement.executeBatch();
            transactionStatement.executeBatch();
        }
    }

    private String nextOrderStatus(Random random) {
        var value = random.nextInt(100);
        if (value < 68) {
            return "SUCCESS";
        }
        if (value < 90) {
            return "FAILED";
        }
        if (value < 96) {
            return "PENDING";
        }
        return "CLOSED";
    }

    private PaymentResult buildPaymentResult(PaymentRoute route, String orderStatus, Random random) {
        if ("SUCCESS".equals(orderStatus)) {
            return new PaymentResult("SUCCESS", null, null, null, null);
        }
        if ("PENDING".equals(orderStatus)) {
            return new PaymentResult("PENDING", null, "支付处理中", null, null);
        }
        if ("CLOSED".equals(orderStatus)) {
            return new PaymentResult("CANCELLED", "USER_CANCELLED", "用户关闭支付", "USER_CLOSE", "user closed payment");
        }

        return switch (route.methodCode()) {
            case "BANK_CARD" -> random.nextBoolean()
                    ? new PaymentResult("FAILED", "INSUFFICIENT_FUNDS", "银行卡余额不足或信用额度不足", "BANK_51", "Insufficient funds")
                    : new PaymentResult("FAILED", "CARD_DECLINED", "银行卡被发卡行拒绝", "BANK_05", "Do not honor");
            case "WECHAT_PAY" -> random.nextBoolean()
                    ? new PaymentResult("TIMEOUT", "CHANNEL_TIMEOUT", "微信支付渠道请求超时", "WX_TIMEOUT", "gateway timeout")
                    : new PaymentResult("CANCELLED", "USER_CANCELLED", "用户在微信收银台取消支付", "WX_USER_CANCEL", "user cancel payment");
            case "ALIPAY" -> new PaymentResult("FAILED", "RISK_REJECTED", "支付宝风控拒绝交易", "ALI_RISK_REJECT", "risk control rejected");
            case "PAYPAL" -> new PaymentResult("FAILED", "PAYPAL_REJECTED", "PayPal 拒绝本次交易", "PAYPAL_DECLINED", "payment declined by PayPal");
            case "BALANCE" -> new PaymentResult("FAILED", "BALANCE_NOT_ENOUGH", "站内余额不足", "BALANCE_NOT_ENOUGH", "balance not enough");
            default -> new PaymentResult("FAILED", "SYSTEM_ERROR", "系统内部异常", "SYSTEM_ERROR", "internal error");
        };
    }

    private void addOrder(
            PreparedStatement statement,
            String orderNo,
            String userId,
            BigDecimal amount,
            String status,
            String channelCode,
            String failureReason,
            LocalDateTime createdAt) throws Exception {
        statement.setString(1, orderNo);
        statement.setString(2, userId);
        statement.setBigDecimal(3, amount);
        statement.setString(4, status);
        statement.setString(5, channelCode);
        statement.setString(6, failureReason);
        statement.setTimestamp(7, Timestamp.valueOf(createdAt));
        statement.setTimestamp(8, Timestamp.valueOf(createdAt.plusSeconds(30)));
        statement.addBatch();
    }

    private void addTransaction(
            PreparedStatement statement,
            String transactionNo,
            String orderNo,
            String userId,
            PaymentRoute route,
            BigDecimal amount,
            PaymentResult result,
            LocalDateTime createdAt) throws Exception {
        statement.setString(1, transactionNo);
        statement.setString(2, orderNo);
        statement.setString(3, userId);
        statement.setString(4, route.methodCode());
        statement.setString(5, route.channelCode());
        statement.setBigDecimal(6, amount);
        statement.setString(7, result.status());
        statement.setString(8, result.failureCode());
        statement.setString(9, result.reason());
        statement.setString(10, result.channelErrorCode());
        statement.setString(11, result.channelErrorMessage());
        statement.setTimestamp(12, "SUCCESS".equals(result.status()) ? Timestamp.valueOf(createdAt.plusSeconds(5)) : null);
        statement.setTimestamp(13, Timestamp.valueOf(createdAt.plusSeconds(1)));
        statement.setTimestamp(14, Timestamp.valueOf(createdAt.plusSeconds(30)));
        statement.addBatch();
    }

    private String orderSql() {
        return """
                INSERT INTO `order`
                (order_no, user_id, amount, currency, status, payment_channel, failure_reason, created_at, updated_at)
                VALUES (?, ?, ?, 'CNY', ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    amount = VALUES(amount),
                    status = VALUES(status),
                    payment_channel = VALUES(payment_channel),
                    failure_reason = VALUES(failure_reason),
                    updated_at = VALUES(updated_at)
                """;
    }

    private String transactionSql() {
        return """
                INSERT INTO payment_transaction
                (transaction_no, order_no, user_id, method_code, channel_code, amount, currency, status,
                 failure_code, failure_reason, channel_error_code, channel_error_message, paid_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    failure_code = VALUES(failure_code),
                    failure_reason = VALUES(failure_reason),
                    channel_error_code = VALUES(channel_error_code),
                    channel_error_message = VALUES(channel_error_message),
                    paid_at = VALUES(paid_at),
                    updated_at = VALUES(updated_at)
                """;
    }

    private record PaymentRoute(String methodCode, String channelCode) {
    }

    private record PaymentResult(
            String status,
            String failureCode,
            String reason,
            String channelErrorCode,
            String channelErrorMessage) {
    }
}
