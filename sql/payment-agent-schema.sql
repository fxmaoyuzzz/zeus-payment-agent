CREATE TABLE `order` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '业务订单号',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    amount DECIMAL(18, 2) NOT NULL COMMENT '订单金额',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status VARCHAR(32) NOT NULL COMMENT '订单状态：PENDING/SUCCESS/FAILED/CLOSED',
    payment_channel VARCHAR(64) DEFAULT NULL COMMENT '当前或最终支付渠道',
    failure_reason VARCHAR(512) DEFAULT NULL COMMENT '订单级失败原因',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    INDEX idx_order_user_id (user_id),
    INDEX idx_order_status_created_at (status, created_at),
    INDEX idx_order_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

CREATE TABLE payment_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    method_code VARCHAR(64) NOT NULL UNIQUE COMMENT '支付方式编码：BANK_CARD/WECHAT_PAY/ALIPAY/PAYPAL/BALANCE/APPLE_PAY/GOOGLE_PAY',
    method_name VARCHAR(128) NOT NULL COMMENT '支付方式名称',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付方式表';

CREATE TABLE payment_channel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_code VARCHAR(64) NOT NULL UNIQUE COMMENT '支付渠道编码',
    channel_name VARCHAR(128) NOT NULL COMMENT '支付渠道名称',
    method_code VARCHAR(64) NOT NULL COMMENT '所属支付方式编码',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    priority INT NOT NULL DEFAULT 100 COMMENT '渠道优先级，数字越小优先级越高',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    INDEX idx_channel_method_code (method_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道表';

CREATE TABLE payment_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_no VARCHAR(64) NOT NULL UNIQUE COMMENT '支付流水号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    method_code VARCHAR(64) NOT NULL COMMENT '支付方式编码',
    channel_code VARCHAR(64) NOT NULL COMMENT '支付渠道编码',
    amount DECIMAL(18, 2) NOT NULL COMMENT '支付金额',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status VARCHAR(32) NOT NULL COMMENT '支付状态：PENDING/SUCCESS/FAILED/CANCELLED/TIMEOUT',
    failure_code VARCHAR(128) DEFAULT NULL COMMENT '内部失败码',
    failure_reason VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    channel_error_code VARCHAR(128) DEFAULT NULL COMMENT '渠道错误码',
    channel_error_message VARCHAR(512) DEFAULT NULL COMMENT '渠道错误信息',
    paid_at DATETIME DEFAULT NULL COMMENT '支付成功时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    INDEX idx_tx_order_no (order_no),
    INDEX idx_tx_user_id (user_id),
    INDEX idx_tx_status_created_at (status, created_at),
    INDEX idx_tx_method_channel (method_code, channel_code),
    INDEX idx_tx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

CREATE TABLE payment_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    log_no VARCHAR(64) NOT NULL UNIQUE COMMENT '日志编号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    transaction_no VARCHAR(64) DEFAULT NULL COMMENT '支付流水号',
    method_code VARCHAR(64) DEFAULT NULL COMMENT '支付方式编码',
    channel_code VARCHAR(64) DEFAULT NULL COMMENT '支付渠道编码',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型：CREATE_PAY/REQUEST_CHANNEL/CHANNEL_RESPONSE/CALLBACK/QUERY/REFUND/ERROR',
    event_status VARCHAR(32) NOT NULL COMMENT '事件状态：SUCCESS/FAILED/TIMEOUT/UNKNOWN',
    request_body TEXT DEFAULT NULL COMMENT '请求报文',
    response_body TEXT DEFAULT NULL COMMENT '响应报文',
    channel_error_code VARCHAR(128) DEFAULT NULL COMMENT '渠道错误码',
    channel_error_message VARCHAR(512) DEFAULT NULL COMMENT '渠道错误信息',
    latency_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    trace_id VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    created_at DATETIME NOT NULL COMMENT '创建时间',

    INDEX idx_log_order_no (order_no),
    INDEX idx_log_transaction_no (transaction_no),
    INDEX idx_log_event_type (event_type),
    INDEX idx_log_method_channel (method_code, channel_code),
    INDEX idx_log_created_at (created_at),
    INDEX idx_log_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付日志表';

CREATE TABLE payment_failure_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    method_code VARCHAR(64) DEFAULT NULL COMMENT '支付方式编码，NULL 表示通用规则',
    channel_code VARCHAR(64) DEFAULT NULL COMMENT '支付渠道编码，NULL 表示通用规则',
    failure_code VARCHAR(128) DEFAULT NULL COMMENT '内部失败码',
    channel_error_code VARCHAR(128) DEFAULT NULL COMMENT '渠道错误码',
    reason_type VARCHAR(64) NOT NULL COMMENT '原因类型：USER/CHANNEL/SYSTEM/RISK/NETWORK/BALANCE/UNKNOWN',
    reason_message VARCHAR(512) NOT NULL COMMENT '失败原因说明',
    suggestion VARCHAR(1024) NOT NULL COMMENT '建议处理动作',
    priority INT NOT NULL DEFAULT 100 COMMENT '规则优先级，数字越小优先级越高',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    UNIQUE KEY uk_rule_match (method_code, channel_code, failure_code, channel_error_code),
    INDEX idx_rule_method_channel (method_code, channel_code),
    INDEX idx_rule_failure_code (failure_code),
    INDEX idx_rule_channel_error_code (channel_error_code),
    INDEX idx_rule_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付失败规则表';

CREATE TABLE payment_daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_date DATE NOT NULL COMMENT '日报日期',
    report_status VARCHAR(32) NOT NULL COMMENT '日报状态：GENERATED/FAILED',
    total_orders BIGINT NOT NULL DEFAULT 0 COMMENT '订单总数',
    success_orders BIGINT NOT NULL DEFAULT 0 COMMENT '成功订单数',
    failed_orders BIGINT NOT NULL DEFAULT 0 COMMENT '失败订单数',
    pending_orders BIGINT NOT NULL DEFAULT 0 COMMENT '处理中订单数',
    success_rate DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '成功率',
    failure_rate DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '失败率',
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '订单总金额',
    report_content LONGTEXT NOT NULL COMMENT '日报 JSON 内容',
    generated_at DATETIME NOT NULL COMMENT '生成时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    UNIQUE KEY uk_daily_report_date (report_date),
    INDEX idx_daily_report_generated_at (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付日报表';

CREATE TABLE payment_anomaly_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    anomaly_no VARCHAR(64) NOT NULL UNIQUE COMMENT '异常事件编号',
    anomaly_date DATE NOT NULL COMMENT '异常所属日期',
    anomaly_type VARCHAR(64) NOT NULL COMMENT '异常类型：HIGH_FAILURE_RATE/CHANNEL_FAILURE_SPIKE/FAILURE_CODE_SPIKE/AMOUNT_ANOMALY',
    severity VARCHAR(32) NOT NULL COMMENT '严重级别：LOW/MEDIUM/HIGH/CRITICAL',
    status VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW/INVESTIGATING/RESOLVED/IGNORED',
    title VARCHAR(256) NOT NULL COMMENT '异常标题',
    description VARCHAR(1024) DEFAULT NULL COMMENT '异常描述',
    metric_name VARCHAR(128) DEFAULT NULL COMMENT '异常指标名称',
    metric_value DECIMAL(18, 4) DEFAULT NULL COMMENT '异常指标值',
    threshold_value DECIMAL(18, 4) DEFAULT NULL COMMENT '触发阈值',
    dimension_type VARCHAR(64) DEFAULT NULL COMMENT '异常维度类型：CHANNEL/METHOD/FAILURE_CODE/USER/DATE',
    dimension_value VARCHAR(128) DEFAULT NULL COMMENT '异常维度值',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    INDEX idx_anomaly_date (anomaly_date),
    INDEX idx_anomaly_type (anomaly_type),
    INDEX idx_anomaly_status (status),
    INDEX idx_anomaly_dimension (dimension_type, dimension_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异常事件表';

CREATE TABLE payment_investigation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    investigation_no VARCHAR(64) NOT NULL UNIQUE COMMENT '调查任务编号',
    anomaly_no VARCHAR(64) DEFAULT NULL COMMENT '关联异常事件编号',
    investigation_date DATE NOT NULL COMMENT '调查日期',
    trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式：MANUAL/AUTO/LLM_TOOL',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/COMPLETED/FAILED',
    question VARCHAR(1024) DEFAULT NULL COMMENT '用户原始问题',
    summary VARCHAR(2048) DEFAULT NULL COMMENT '调查结论摘要',
    conclusion LONGTEXT DEFAULT NULL COMMENT '完整调查结论',
    started_at DATETIME NOT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    INDEX idx_investigation_anomaly_no (anomaly_no),
    INDEX idx_investigation_date (investigation_date),
    INDEX idx_investigation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异常调查任务表';

CREATE TABLE payment_investigation_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    investigation_no VARCHAR(64) NOT NULL COMMENT '调查任务编号',
    step_no INT NOT NULL COMMENT '步骤序号',
    step_type VARCHAR(64) NOT NULL COMMENT '步骤类型：DETECT_ANOMALY/QUERY_TRANSACTION/QUERY_ORDER/SEARCH_KNOWLEDGE/SUMMARIZE',
    step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/COMPLETED/FAILED',
    input_content LONGTEXT DEFAULT NULL COMMENT '步骤输入',
    output_content LONGTEXT DEFAULT NULL COMMENT '步骤输出',
    error_message VARCHAR(1024) DEFAULT NULL COMMENT '失败信息',
    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',

    UNIQUE KEY uk_investigation_step (investigation_no, step_no),
    INDEX idx_step_investigation_no (investigation_no),
    INDEX idx_step_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异常调查步骤表';

CREATE TABLE payment_investigation_evidence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    investigation_no VARCHAR(64) NOT NULL COMMENT '调查任务编号',
    step_no INT DEFAULT NULL COMMENT '来源步骤序号',
    evidence_type VARCHAR(64) NOT NULL COMMENT '证据类型：DAILY_REPORT/ORDER/TRANSACTION/KNOWLEDGE/METRIC',
    evidence_source VARCHAR(128) DEFAULT NULL COMMENT '证据来源：MySQL/Chroma/Tool',
    reference_id VARCHAR(128) DEFAULT NULL COMMENT '关联业务编号，例如订单号、流水号、文档 Chunk ID',
    title VARCHAR(256) DEFAULT NULL COMMENT '证据标题',
    content LONGTEXT NOT NULL COMMENT '证据内容 JSON 或文本',
    confidence DECIMAL(10, 4) DEFAULT NULL COMMENT '证据置信度',
    created_at DATETIME NOT NULL COMMENT '创建时间',

    INDEX idx_evidence_investigation_no (investigation_no),
    INDEX idx_evidence_type (evidence_type),
    INDEX idx_evidence_reference_id (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异常调查证据表';

CREATE TABLE tool_call_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(128) NOT NULL COMMENT 'Tool 调用审计追踪ID',
    tool_name VARCHAR(128) NOT NULL COMMENT 'Tool 名称',
    tool_class VARCHAR(256) NOT NULL COMMENT 'Tool 类名',
    tool_method VARCHAR(128) NOT NULL COMMENT 'Tool 方法名',
    request_summary LONGTEXT DEFAULT NULL COMMENT 'Tool 入参摘要',
    response_summary LONGTEXT DEFAULT NULL COMMENT 'Tool 出参摘要',
    status VARCHAR(32) NOT NULL COMMENT '调用状态：SUCCESS/FAILED',
    error_message VARCHAR(1024) DEFAULT NULL COMMENT '失败信息',
    latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
    created_at DATETIME NOT NULL COMMENT '创建时间',

    INDEX idx_tool_audit_trace_id (trace_id),
    INDEX idx_tool_audit_tool_name (tool_name),
    INDEX idx_tool_audit_status (status),
    INDEX idx_tool_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tool 调用审计表';

INSERT INTO payment_method (method_code, method_name, enabled, created_at, updated_at) VALUES
('BANK_CARD', '银行卡支付', 1, NOW(), NOW()),
('WECHAT_PAY', '微信支付', 1, NOW(), NOW()),
('ALIPAY', '支付宝', 1, NOW(), NOW()),
('PAYPAL', 'PayPal', 1, NOW(), NOW()),
('BALANCE', '余额支付', 1, NOW(), NOW()),
('APPLE_PAY', 'Apple Pay', 1, NOW(), NOW()),
('GOOGLE_PAY', 'Google Pay', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    method_name = VALUES(method_name),
    enabled = VALUES(enabled),
    updated_at = VALUES(updated_at);

INSERT INTO payment_channel (channel_code, channel_name, method_code, enabled, priority, created_at, updated_at) VALUES
('BANK_ABC', '农业银行通道', 'BANK_CARD', 1, 10, NOW(), NOW()),
('BANK_ICBC', '工商银行通道', 'BANK_CARD', 1, 20, NOW(), NOW()),
('WECHAT_OFFICIAL', '微信官方支付', 'WECHAT_PAY', 1, 10, NOW(), NOW()),
('ALIPAY_OFFICIAL', '支付宝官方支付', 'ALIPAY', 1, 10, NOW(), NOW()),
('PAYPAL_OFFICIAL', 'PayPal 官方支付', 'PAYPAL', 1, 10, NOW(), NOW()),
('BALANCE_INTERNAL', '站内余额支付', 'BALANCE', 1, 10, NOW(), NOW()),
('APPLE_PAY_STRIPE', 'Stripe Apple Pay', 'APPLE_PAY', 1, 10, NOW(), NOW()),
('GOOGLE_PAY_STRIPE', 'Stripe Google Pay', 'GOOGLE_PAY', 1, 10, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    channel_name = VALUES(channel_name),
    method_code = VALUES(method_code),
    enabled = VALUES(enabled),
    priority = VALUES(priority),
    updated_at = VALUES(updated_at);

INSERT INTO payment_failure_rule
(method_code, channel_code, failure_code, channel_error_code, reason_type, reason_message, suggestion, priority, enabled, created_at, updated_at)
VALUES
('BANK_CARD', NULL, 'INSUFFICIENT_FUNDS', NULL, 'BALANCE', '银行卡余额不足或信用额度不足。', '建议用户更换银行卡，或确认账户余额和信用额度后重试。', 10, 1, NOW(), NOW()),
('BANK_CARD', NULL, 'CARD_DECLINED', NULL, 'USER', '银行卡被发卡行拒绝。', '建议用户联系发卡行确认限制，或更换支付方式。', 20, 1, NOW(), NOW()),
('WECHAT_PAY', NULL, 'USER_CANCELLED', NULL, 'USER', '用户在微信支付过程中取消支付。', '建议用户重新发起支付。', 10, 1, NOW(), NOW()),
('ALIPAY', NULL, 'RISK_REJECTED', NULL, 'RISK', '支付宝风控拒绝交易。', '建议用户更换支付方式，或稍后重试。', 10, 1, NOW(), NOW()),
('PAYPAL', NULL, 'PAYPAL_REJECTED', NULL, 'CHANNEL', 'PayPal 拒绝本次交易。', '建议检查 PayPal 账户状态，或更换支付方式。', 10, 1, NOW(), NOW()),
('BALANCE', NULL, 'BALANCE_NOT_ENOUGH', NULL, 'BALANCE', '站内余额不足。', '建议用户充值后重试，或更换其他支付方式。', 10, 1, NOW(), NOW()),
(NULL, NULL, 'CHANNEL_TIMEOUT', NULL, 'NETWORK', '支付渠道请求超时。', '建议查询渠道状态，并确认是否需要发起补单或重试。', 50, 1, NOW(), NOW()),
(NULL, NULL, 'SYSTEM_ERROR', NULL, 'SYSTEM', '系统内部异常。', '建议查看应用日志和链路 Trace，确认服务是否异常。', 60, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    reason_type = VALUES(reason_type),
    reason_message = VALUES(reason_message),
    suggestion = VALUES(suggestion),
    priority = VALUES(priority),
    enabled = VALUES(enabled),
    updated_at = VALUES(updated_at);

INSERT INTO `order` (order_no, user_id, amount, currency, status, payment_channel, failure_reason, created_at, updated_at) VALUES
('P202608250001', 'U10001', 128.00, 'CNY', 'FAILED', 'BANK_ABC', '银行卡余额不足或信用额度不足', '2026-08-25 09:10:00', '2026-08-25 09:10:35'),
('P202608250002', 'U10002', 89.90, 'CNY', 'FAILED', 'WECHAT_OFFICIAL', '用户取消支付', '2026-08-25 10:15:00', '2026-08-25 10:15:20'),
('P202608250003', 'U10003', 560.00, 'CNY', 'FAILED', 'ALIPAY_OFFICIAL', '渠道风控拒绝交易', '2026-08-25 11:20:00', '2026-08-25 11:20:28'),
('P202608250004', 'U10004', 39.00, 'CNY', 'FAILED', 'WECHAT_OFFICIAL', '支付渠道请求超时', '2026-08-25 12:30:00', '2026-08-25 12:31:05'),
('P202608250005', 'U10005', 16.00, 'CNY', 'SUCCESS', 'BALANCE_INTERNAL', NULL, '2026-08-25 13:00:00', '2026-08-25 13:00:05'),
('P202608250006', 'U10006', 230.00, 'USD', 'FAILED', 'PAYPAL_OFFICIAL', 'PayPal 拒绝本次交易', '2026-08-25 14:10:00', '2026-08-25 14:10:42')
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    amount = VALUES(amount),
    currency = VALUES(currency),
    status = VALUES(status),
    payment_channel = VALUES(payment_channel),
    failure_reason = VALUES(failure_reason),
    updated_at = VALUES(updated_at);

INSERT INTO payment_transaction
(transaction_no, order_no, user_id, method_code, channel_code, amount, currency, status, failure_code, failure_reason, channel_error_code, channel_error_message, paid_at, created_at, updated_at)
VALUES
('T202608250001', 'P202608250001', 'U10001', 'BANK_CARD', 'BANK_ABC', 128.00, 'CNY', 'FAILED', 'INSUFFICIENT_FUNDS', '银行卡余额不足或信用额度不足', 'BANK_51', 'Insufficient funds', NULL, '2026-08-25 09:10:02', '2026-08-25 09:10:35'),
('T202608250002', 'P202608250002', 'U10002', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 89.90, 'CNY', 'CANCELLED', 'USER_CANCELLED', '用户在微信收银台取消支付', 'WX_USER_CANCEL', 'user cancel payment', NULL, '2026-08-25 10:15:03', '2026-08-25 10:15:20'),
('T202608250003', 'P202608250003', 'U10003', 'ALIPAY', 'ALIPAY_OFFICIAL', 560.00, 'CNY', 'FAILED', 'RISK_REJECTED', '支付宝风控拒绝交易', 'ALI_RISK_REJECT', 'risk control rejected', NULL, '2026-08-25 11:20:03', '2026-08-25 11:20:28'),
('T202608250004', 'P202608250004', 'U10004', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 39.00, 'CNY', 'TIMEOUT', 'CHANNEL_TIMEOUT', '支付渠道请求超时', 'WX_TIMEOUT', 'gateway timeout', NULL, '2026-08-25 12:30:04', '2026-08-25 12:31:05'),
('T202608250005', 'P202608250005', 'U10005', 'BALANCE', 'BALANCE_INTERNAL', 16.00, 'CNY', 'SUCCESS', NULL, NULL, NULL, NULL, '2026-08-25 13:00:05', '2026-08-25 13:00:01', '2026-08-25 13:00:05'),
('T202608250006', 'P202608250006', 'U10006', 'PAYPAL', 'PAYPAL_OFFICIAL', 230.00, 'USD', 'FAILED', 'PAYPAL_REJECTED', 'PayPal 拒绝本次交易', 'PAYPAL_DECLINED', 'payment declined by PayPal', NULL, '2026-08-25 14:10:03', '2026-08-25 14:10:42')
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    failure_code = VALUES(failure_code),
    failure_reason = VALUES(failure_reason),
    channel_error_code = VALUES(channel_error_code),
    channel_error_message = VALUES(channel_error_message),
    paid_at = VALUES(paid_at),
    updated_at = VALUES(updated_at);

INSERT INTO payment_log
(log_no, order_no, transaction_no, method_code, channel_code, event_type, event_status, request_body, response_body, channel_error_code, channel_error_message, latency_ms, trace_id, created_at)
VALUES
('L20260825000101', 'P202608250001', 'T202608250001', 'BANK_CARD', 'BANK_ABC', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 12, 'trace-bank-card-001', '2026-08-25 09:10:02'),
('L20260825000102', 'P202608250001', 'T202608250001', 'BANK_CARD', 'BANK_ABC', 'REQUEST_CHANNEL', 'SUCCESS', NULL, NULL, NULL, NULL, 68, 'trace-bank-card-001', '2026-08-25 09:10:10'),
('L20260825000103', 'P202608250001', 'T202608250001', 'BANK_CARD', 'BANK_ABC', 'CHANNEL_RESPONSE', 'FAILED', NULL, NULL, 'BANK_51', 'Insufficient funds', 320, 'trace-bank-card-001', '2026-08-25 09:10:35'),
('L20260825000201', 'P202608250002', 'T202608250002', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 10, 'trace-wechat-002', '2026-08-25 10:15:03'),
('L20260825000202', 'P202608250002', 'T202608250002', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 'CALLBACK', 'FAILED', NULL, NULL, 'WX_USER_CANCEL', 'user cancel payment', 180, 'trace-wechat-002', '2026-08-25 10:15:20'),
('L20260825000301', 'P202608250003', 'T202608250003', 'ALIPAY', 'ALIPAY_OFFICIAL', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 15, 'trace-alipay-003', '2026-08-25 11:20:03'),
('L20260825000302', 'P202608250003', 'T202608250003', 'ALIPAY', 'ALIPAY_OFFICIAL', 'CHANNEL_RESPONSE', 'FAILED', NULL, NULL, 'ALI_RISK_REJECT', 'risk control rejected', 245, 'trace-alipay-003', '2026-08-25 11:20:28'),
('L20260825000401', 'P202608250004', 'T202608250004', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 14, 'trace-wechat-004', '2026-08-25 12:30:04'),
('L20260825000402', 'P202608250004', 'T202608250004', 'WECHAT_PAY', 'WECHAT_OFFICIAL', 'REQUEST_CHANNEL', 'TIMEOUT', NULL, NULL, 'WX_TIMEOUT', 'gateway timeout', 60000, 'trace-wechat-004', '2026-08-25 12:31:05'),
('L20260825000501', 'P202608250005', 'T202608250005', 'BALANCE', 'BALANCE_INTERNAL', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 8, 'trace-balance-005', '2026-08-25 13:00:01'),
('L20260825000502', 'P202608250005', 'T202608250005', 'BALANCE', 'BALANCE_INTERNAL', 'CALLBACK', 'SUCCESS', NULL, NULL, NULL, NULL, 20, 'trace-balance-005', '2026-08-25 13:00:05'),
('L20260825000601', 'P202608250006', 'T202608250006', 'PAYPAL', 'PAYPAL_OFFICIAL', 'CREATE_PAY', 'SUCCESS', NULL, NULL, NULL, NULL, 30, 'trace-paypal-006', '2026-08-25 14:10:03'),
('L20260825000602', 'P202608250006', 'T202608250006', 'PAYPAL', 'PAYPAL_OFFICIAL', 'CHANNEL_RESPONSE', 'FAILED', NULL, NULL, 'PAYPAL_DECLINED', 'payment declined by PayPal', 520, 'trace-paypal-006', '2026-08-25 14:10:42')
ON DUPLICATE KEY UPDATE
    event_status = VALUES(event_status),
    channel_error_code = VALUES(channel_error_code),
    channel_error_message = VALUES(channel_error_message),
    latency_ms = VALUES(latency_ms),
    trace_id = VALUES(trace_id),
    created_at = VALUES(created_at);
