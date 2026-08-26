/**
 * 支付分析模块。
 *
 * <p>执行流程：LLM 识别失败分析意图 -> PaymentFailureAnalysisTool 查询订单、支付流水和日志
 * -> 匹配失败规则 -> 汇总原因、证据、置信度和处理建议。</p>
 */
package com.moyu.zeuspaymentagent.payment;
