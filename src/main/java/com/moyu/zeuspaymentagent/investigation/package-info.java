/**
 * 支付异常调查模块。
 *
 * <p>执行流程：用户触发调查 -> 聚合订单和支付流水指标 -> 识别异常信号 ->
 * 查询异常流水样本 -> 检索知识库处理建议 -> 保存调查过程 -> 返回调查结论。
 */
package com.moyu.zeuspaymentagent.investigation;
