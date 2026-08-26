package com.moyu.zeuspaymentagent.chat.service;

import com.moyu.zeuspaymentagent.chat.model.ConversationMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final String KEY_PREFIX = "zeus:payment-agent:conversation:";
    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, List<ConversationMessage>> fallbackMemory = new ConcurrentHashMap<>();

    public ConversationMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读取流程：优先从 Redis 获取最近对话，Redis 不可用时降级到本地内存。
     */
    public List<ConversationMessage> getRecentMessages(String conversationId) {
        try {
            var values = redisTemplate.opsForList().range(key(conversationId), 0, -1);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .map(this::deserialize)
                    .toList();
        }
        catch (RuntimeException ex) {
            log.warn("Redis conversation memory unavailable, falling back to in-memory history: {}", ex.getMessage());
            return List.copyOf(fallbackMemory.getOrDefault(conversationId, List.of()));
        }
    }

    /**
     * 写入流程：用户消息和助手回复成对保存，并限制历史长度。
     */
    public void appendExchange(String conversationId, String userMessage, String assistantMessage) {
        append(conversationId, new ConversationMessage(ConversationMessage.Role.USER, userMessage));
        append(conversationId, new ConversationMessage(ConversationMessage.Role.ASSISTANT, assistantMessage));
        trim(conversationId);
    }

    private void append(String conversationId, ConversationMessage message) {
        try {
            redisTemplate.opsForList().rightPush(key(conversationId), serialize(message));
            redisTemplate.expire(key(conversationId), TTL);
        }
        catch (RuntimeException ex) {
            fallbackMemory.computeIfAbsent(conversationId, ignored -> new ArrayList<>()).add(message);
        }
    }

    private void trim(String conversationId) {
        try {
            redisTemplate.opsForList().trim(key(conversationId), -MAX_MESSAGES, -1);
        }
        catch (RuntimeException ex) {
            var messages = fallbackMemory.get(conversationId);
            if (messages != null && messages.size() > MAX_MESSAGES) {
                fallbackMemory.put(conversationId, new ArrayList<>(messages.subList(messages.size() - MAX_MESSAGES, messages.size())));
            }
        }
    }

    private ConversationMessage deserialize(String value) {
        var separatorIndex = value.indexOf(':');
        if (separatorIndex < 0) {
            throw new IllegalStateException("Invalid conversation message format");
        }

        var role = ConversationMessage.Role.valueOf(value.substring(0, separatorIndex));
        var content = new String(
                Base64.getDecoder().decode(value.substring(separatorIndex + 1)),
                StandardCharsets.UTF_8);
        return new ConversationMessage(role, content);
    }

    private String serialize(ConversationMessage message) {
        var content = Base64.getEncoder().encodeToString(message.content().getBytes(StandardCharsets.UTF_8));
        return message.role().name() + ":" + content;
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
