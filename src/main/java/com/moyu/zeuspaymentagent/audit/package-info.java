/**
 * Tool 调用审计模块。
 *
 * <p>执行流程：Tool 显式调用审计服务 -> 记录入参摘要、出参摘要、耗时和状态 ->
 * 写入 MySQL，方便后续追踪、审计和 Eval 分析。
 */
package com.moyu.zeuspaymentagent.audit;
