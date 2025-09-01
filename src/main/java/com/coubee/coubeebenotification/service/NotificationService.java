package com.coubee.coubeebenotification.service;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NotificationService {
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeatScheduler;

    @PostConstruct
    public void initialize() {
        // heartbeat을 위한 스케줄러 초기화 (30초마다 실행)
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("sse-heartbeat-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        
        heartbeatScheduler.scheduleWithFixedDelay(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
        log.info("SSE heartbeat scheduler initialized");
    }

    @PreDestroy
    public void cleanup() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("SSE heartbeat scheduler shutdown");
        }
    }

    public SseEmitter createConnection(Long userId) {
        String userIdStr = userId.toString();
        
        // 기존 연결이 있다면 정리
        SseEmitter existingEmitter = emitterMap.get(userIdStr);
        if (existingEmitter != null) {
            try {
                existingEmitter.send(SseEmitter.event()
                        .name("CONNECTION_REPLACED")
                        .data("새로운 연결로 교체됩니다"));
                existingEmitter.complete();
            } catch (IOException e) {
                log.debug("Failed to send replacement notice to existing connection for userId: {}", userId, e);
            }
            emitterMap.remove(userIdStr);
            log.info("Removed existing SSE connection for userId: {}", userId);
        }
        
        SseEmitter emitter = new SseEmitter(60 * 1000L * 30); // 30분 타임아웃
        emitterMap.put(userIdStr, emitter);

        try {
            emitter.send(SseEmitter.event().name("INIT").data("SSE 연결됨"));
            log.info("SSE connection created for userId: {}", userId);
        } catch (IOException e) {
            log.error("Failed to send initial message for userId: {}", userId, e);
            emitter.completeWithError(e);
        }

        emitter.onCompletion(() -> {
            emitterMap.remove(userId.toString());
            log.info("SSE connection completed for userId: {}", userId);
        });
        
        emitter.onTimeout(() -> {
            emitterMap.remove(userId.toString());
            log.info("SSE connection timeout for userId: {}", userId);
        });
        
        emitter.onError((throwable) -> {
            emitterMap.remove(userId.toString());
            log.error("SSE connection error for userId: {}", userId, throwable);
        });

        return emitter;
    }

//    public void sendNotification(Long userId, String messageType, String title, String message) {
//        SseEmitter emitter = emitterMap.get(userId.toString());
//
//        if (emitter != null) {
//            try {
//                Map<String, Object> notificationData = Map.of(
//                        "messageType", messageType,
//                        "title", title,
//                        "message", message,
//                        "timestamp", System.currentTimeMillis()
//                );
//
//                emitter.send(SseEmitter.event()
//                        .name("NOTIFICATION")
//                        .data(notificationData));
//
//                log.info("Notification sent to userId: {}, type: {}", userId, messageType);
//            } catch (IOException e) {
//                log.error("Failed to send notification to userId: {}", userId, e);
//                emitterMap.remove(userId.toString());
//                emitter.completeWithError(e);
//            }
//        } else {
//            log.warn("No SSE connection found for userId: {}", userId);
//        }
//    }

    public void sendOrderNotification(OrderStatusUpdateEvent eventData) {
        SseEmitter emitter = emitterMap.get(eventData.getUserId().toString());
        
        if (emitter != null) {
            try {
                Map<String, Object> notificationData = Map.of(
                        "userId", eventData.getUserId().toString(),
                        "messageType", eventData.getNotificationType(),
                        "title", eventData.getTitle(),
                        "message", eventData.getMessage(),
                        "messageData", eventData,
                        "timestamp", System.currentTimeMillis()
                );

                emitter.send(SseEmitter.event()
                        .name("ORDER_NOTIFICATION")
                        .data(notificationData));

                log.info("Order notification sent to userId: {}, type: {}", eventData.getUserId().toString(), eventData.getNotificationType());
            } catch (IOException e) {
                log.error("Failed to send order notification to userId: {}", eventData.getUserId().toString(), e);
                emitterMap.remove(eventData.getUserId().toString());
                emitter.completeWithError(e);
            }
        } else {
            log.warn("No SSE connection found for userId: {}", eventData.getUserId().toString());
        }
    }

    /**
     * 모든 활성 연결에 heartbeat 메시지를 전송하여 연결 유지
     */
    private void sendHeartbeat() {
        if (emitterMap.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} active connections", emitterMap.size());
        
        // 실패한 연결들을 저장할 리스트
        java.util.List<String> failedConnections = new java.util.ArrayList<>();
        
        emitterMap.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("HEARTBEAT")
                        .data(Map.of(
                                "type", "heartbeat",
                                "timestamp", System.currentTimeMillis()
                        )));
                
                log.trace("Heartbeat sent to userId: {}", userId);
            } catch (IOException e) {
                log.warn("Failed to send heartbeat to userId: {}, connection will be removed", userId, e);
                failedConnections.add(userId);
            }
        });
        
        // 실패한 연결들을 제거
        failedConnections.forEach(userId -> {
            SseEmitter emitter = emitterMap.remove(userId);
            if (emitter != null) {
                emitter.completeWithError(new IOException("Heartbeat failed"));
            }
        });
        
        if (!failedConnections.isEmpty()) {
            log.info("Removed {} failed connections during heartbeat", failedConnections.size());
        }
    }

    /**
     * 연결 수 조회
     */
    public int getActiveConnectionCount() {
        return emitterMap.size();
    }

    /**
     * 특정 사용자의 연결 상태 확인
     */
    public boolean hasActiveConnection(Long userId) {
        return emitterMap.containsKey(userId.toString());
    }
}
