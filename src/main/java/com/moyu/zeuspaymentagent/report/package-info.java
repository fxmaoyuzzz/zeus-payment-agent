/**
 * 支付日报模块。
 *
 * <p>执行流程：管理接口、定时任务或 LLM Tool 触发 -> 聚合订单、支付流水和支付日志
 * -> 计算成功率、失败率、渠道分布和异常 TopN -> 保存日报结果。</p>
 */
package com.moyu.zeuspaymentagent.report;
