package com.aicode.review.config;

import com.aicode.review.model.ReviewProgressMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审查进度 WebSocket 处理器
 * 
 * 管理 WebSocket 连接，实时推送审查进度消息
 * 支持消息缓存：当任务没有活跃连接时，缓存消息，等订阅后再发送
 * 
 * @author AI Code Review Team
 */
@Slf4j
@Component
public class ReviewProgressWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    
    // 存储所有活跃的会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 存储任务ID到会话的映射
    private final Map<String, String> taskSessionMap = new ConcurrentHashMap<>();
    
    // 消息缓存：任务ID -> 消息列表（用于连接建立前的消息缓存）
    private final Map<String, List<ReviewProgressMessage>> messageCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间：5分钟
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;
    
    // 最大缓存消息数
    private static final int MAX_CACHE_SIZE = 100;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket 连接建立: {}", sessionId);
        
        // 发送连接成功消息
        sendMessage(session, ReviewProgressMessage.connected(sessionId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到 WebSocket 消息: {}", payload);
        
        try {
            // 解析客户端消息，获取任务ID
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String taskId = (String) data.get("taskId");
            
            if (taskId != null) {
                taskSessionMap.put(taskId, session.getId());
                log.info("任务 {} 绑定到会话 {}，当前任务映射数: {}", taskId, session.getId(), taskSessionMap.size());
                
                // 确认订阅
                sendMessage(session, ReviewProgressMessage.subscribed(taskId));
                
                // 发送缓存的消息（如果有）
                flushCachedMessages(taskId, session);
            } else {
                log.warn("收到消息但没有 taskId: {}", payload);
            }
        } catch (Exception e) {
            log.warn("处理 WebSocket 消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 刷新缓存的消息到指定会话
     */
    private void flushCachedMessages(String taskId, WebSocketSession session) {
        List<ReviewProgressMessage> cachedMessages = messageCache.remove(taskId);
        log.info("刷新缓存消息 - 任务: {}, 缓存消息数: {}", taskId, 
                cachedMessages != null ? cachedMessages.size() : 0);
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            log.info("任务 {} 发送 {} 条缓存消息", taskId, cachedMessages.size());
            for (ReviewProgressMessage msg : cachedMessages) {
                sendMessage(session, msg);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        // 清理任务映射
        taskSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        
        log.info("WebSocket 连接关闭: {}, 状态: {}", sessionId, status);
    }

    /**
     * 发送进度消息到指定任务
     * 如果任务没有活跃连接，消息会被缓存，等连接建立后发送
     * 
     * @param taskId 任务ID
     * @param message 进度消息
     */
    public void sendProgress(String taskId, ReviewProgressMessage message) {
        String sessionId = taskSessionMap.get(taskId);
        
        // 如果没有活跃会话，短暂等待后重试（处理订阅和启动的竞态条件）
        if (sessionId == null) {
            log.info("任务 {} 无会话映射，开始重试... 当前映射数: {}", taskId, taskSessionMap.size());
            // 最多重试 30 次，每次 100ms，总共等待最多 3 秒，给 WebSocket 订阅完成的时间
            for (int i = 0; i < 30; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                sessionId = taskSessionMap.get(taskId);
                if (sessionId != null) {
                    log.info("任务 {} 重试 {} 次后找到会话: {}", taskId, i + 1, sessionId);
                    break;
                }
            }
            
            if (sessionId == null) {
                log.warn("任务 {} 重试 10 次后仍无会话，缓存消息。当前任务映射: {}", 
                        taskId, taskSessionMap.keySet());
                cacheMessage(taskId, message);
                return;
            }
        }
        
        log.info("发送进度消息 - 任务: {}, 类型: {}, 会话: {}", 
                 taskId, message.getType(), sessionId);
        
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.warn("会话 {} 已关闭或不存在，缓存消息", sessionId);
            taskSessionMap.remove(taskId);
            cacheMessage(taskId, message);
            return;
        }
        
        sendMessage(session, message);
    }
    
    /**
     * 缓存消息到指定任务
     * 缓存所有消息类型，包括 COMPLETED/ERROR，确保客户端重连后能看到最终结果
     */
    private void cacheMessage(String taskId, ReviewProgressMessage message) {
        messageCache.computeIfAbsent(taskId, k -> new ArrayList<>()).add(message);
        
        // 限制缓存大小
        List<ReviewProgressMessage> cache = messageCache.get(taskId);
        if (cache != null && cache.size() > MAX_CACHE_SIZE) {
            // 保留最新的消息
            messageCache.put(taskId, new ArrayList<>(cache.subList(cache.size() - MAX_CACHE_SIZE/2, cache.size())));
            log.warn("任务 {} 消息缓存超过限制，清理旧消息", taskId);
        }
        
        log.debug("任务 {} 消息已缓存({})，当前缓存 {} 条", taskId, message.getType(),
                 messageCache.getOrDefault(taskId, new ArrayList<>()).size());
    }

    /**
     * 广播消息到所有连接的客户端
     * 
     * @param message 消息
     */
    public void broadcast(ReviewProgressMessage message) {
        String json = toJson(message);
        if (json == null) return;
        
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.warn("发送消息失败: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 发送消息到指定会话
     */
    private void sendMessage(WebSocketSession session, ReviewProgressMessage message) {
        String json = toJson(message);
        if (json == null) {
            log.error("消息序列化失败，无法发送: {}", message.getType());
            return;
        }
        
        try {
            log.info("发送 JSON 消息到会话 {}: 类型={}, 内容预览={}", 
                    session.getId(), message.getType(),
                    json.substring(0, Math.min(json.length(), 100)));
            session.sendMessage(new TextMessage(json));
            log.info("消息发送成功: {}", message.getType());
        } catch (IOException e) {
            log.warn("发送消息到会话 {} 失败: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * 将消息对象转为 JSON
     */
    private String toJson(ReviewProgressMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return null;
        }
    }
}
