package com.moyu.zeuspaymentagent.chat.controller;

import com.moyu.zeuspaymentagent.chat.model.ConversationMessage;
import com.moyu.zeuspaymentagent.chat.service.ConversationMemoryService;
import com.moyu.zeuspaymentagent.knowledge.tool.KnowledgeSearchTool;
import com.moyu.zeuspaymentagent.order.tool.OrderQueryTool;
import com.moyu.zeuspaymentagent.payment.tool.PaymentFailureAnalysisTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String SYSTEM_PROMPT = """
            你是支付订单查询助手。
            你只能基于工具返回的订单数据回答，不要编造订单。
            如果用户询问订单号、订单状态、用户订单列表，优先调用订单查询工具。
            如果用户询问支付为什么失败、失败原因、失败归因、如何处理，优先调用支付失败分析工具。
            如果用户询问支付渠道规则、错误码解释、失败处理 SOP、排查步骤，优先调用知识库检索工具。
            回答要简洁，包含订单号、状态、金额、渠道、创建时间。
            分析失败订单时要说明原因类型、置信度、关键证据和建议处理动作。
            使用知识库回答时，要结合检索片段说明来源文件和标题。
            如果用户使用“它”“这个订单”“上一笔”等指代，根据最近对话历史理解指代对象。
            """;

    private final ChatClient chatClient;
    private final OrderQueryTool orderQueryTool;
    private final PaymentFailureAnalysisTool paymentFailureAnalysisTool;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final ConversationMemoryService conversationMemoryService;

    public ChatController(
            ChatClient.Builder chatClientBuilder,
            OrderQueryTool orderQueryTool,
            PaymentFailureAnalysisTool paymentFailureAnalysisTool,
            KnowledgeSearchTool knowledgeSearchTool,
            ConversationMemoryService conversationMemoryService) {
        this.chatClient = chatClientBuilder.build();
        this.orderQueryTool = orderQueryTool;
        this.paymentFailureAnalysisTool = paymentFailureAnalysisTool;
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        var conversationId = request.conversationId();
        var messages = buildMessages(conversationMemoryService.getRecentMessages(conversationId), request.message());

        var content = chatClient.prompt()
                .messages(messages)
                .tools(orderQueryTool, paymentFailureAnalysisTool, knowledgeSearchTool)
                .call()
                .content();

        conversationMemoryService.appendExchange(conversationId, request.message(), content);

        return new ChatResponse(conversationId, content);
    }

    /**
     * 流式对话流程：边接收模型输出边推送 SSE，结束后再保存完整回答。
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        var conversationId = request.conversationId();
        var messages = buildMessages(conversationMemoryService.getRecentMessages(conversationId), request.message());
        var emitter = new SseEmitter(120_000L);
        var answer = new StringBuilder();

        chatClient.prompt()
                .messages(messages)
                .tools(orderQueryTool, paymentFailureAnalysisTool, knowledgeSearchTool)
                .stream()
                .content()
                .subscribe(
                        chunk -> sendChunk(emitter, answer, chunk),
                        error -> sendError(emitter, error),
                        () -> completeStream(emitter, conversationId, request.message(), answer.toString()));

        return emitter;
    }

    /**
     * 对话上下文流程：系统 Prompt + 历史消息 + 当前用户问题。
     */
    private List<Message> buildMessages(List<ConversationMessage> history, String currentMessage) {
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        for (var message : history) {
            if (message.role() == ConversationMessage.Role.USER) {
                messages.add(new UserMessage(message.content()));
            }
            else {
                messages.add(new AssistantMessage(message.content()));
            }
        }

        messages.add(new UserMessage(currentMessage));
        return messages;
    }

    public record ChatRequest(@NotBlank String conversationId, @NotBlank String message) {
    }

    public record ChatResponse(String conversationId, String answer) {
    }

    private void sendChunk(SseEmitter emitter, StringBuilder answer, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return;
        }

        answer.append(chunk);
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk));
        }
        catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private void sendError(SseEmitter emitter, Throwable error) {
        try {
            emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
        }
        catch (Exception ignored) {
        }
        emitter.completeWithError(error);
    }

    private void completeStream(
            SseEmitter emitter, String conversationId, String userMessage, String assistantMessage) {
        conversationMemoryService.appendExchange(conversationId, userMessage, assistantMessage);
        try {
            emitter.send(SseEmitter.event().name("done").data("{}"));
        }
        catch (Exception ignored) {
        }
        emitter.complete();
    }
}
