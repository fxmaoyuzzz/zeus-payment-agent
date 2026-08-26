/**
 * 订单模块。
 *
 * <p>执行流程：LLM 识别订单查询意图 -> OrderQueryTool 接收结构化参数
 * -> PaymentOrderMapper 查询 MySQL -> 转换为 PaymentOrderView 返回给 LLM。</p>
 */
package com.moyu.zeuspaymentagent.order;
