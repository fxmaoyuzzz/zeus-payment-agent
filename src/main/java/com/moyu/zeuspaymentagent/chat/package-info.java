/**
 * 聊天模块。
 *
 * <p>执行流程：前端提交用户问题 -> Controller 读取会话历史 -> ChatClient 调用 LLM
 * -> LLM 按需触发业务 Tool -> Controller 保存本轮问答并返回结果。</p>
 */
package com.moyu.zeuspaymentagent.chat;
