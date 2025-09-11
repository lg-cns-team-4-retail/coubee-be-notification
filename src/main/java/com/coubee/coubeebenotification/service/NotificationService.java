package com.coubee.coubeebenotification.service;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import com.coubee.coubeebenotification.metrics.SseMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SseMetrics sseMetrics;
    private final Map<String, Set<SseEmitter>> connectionEmitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, String> emitterToConnectionKey = new ConcurrentHashMap<>();
    private final Map<SseEmitter, Timer.Sample> connectionTimers = new ConcurrentHashMap<>();
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
        
        heartbeatScheduler.scheduleWithFixedDelay(this::sendHeartbeat, 5, 15, TimeUnit.SECONDS); // 5초 후 시작, 15초마다 실행
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

    public SseEmitter createConnection(Long userId, Long storeId) {
        String connectionKey = createConnectionKey(userId, storeId);
        
        // 메트릭: 연결 시도 기록
        sseMetrics.recordConnectionAttempt();
        
        SseEmitter emitter = new SseEmitter(0L); // 무한 타임아웃 (heartbeat으로 관리)
        
        try {
            // 성공한 연결만 맵에 저장 (먼저 저장)
            connectionEmitters.computeIfAbsent(connectionKey, k -> ConcurrentHashMap.newKeySet()).add(emitter);
            emitterToConnectionKey.put(emitter, connectionKey);
            
            // 메트릭: 연결 성공 및 타이머 시작
            sseMetrics.recordConnectionSuccess();
            Timer.Sample connectionTimer = sseMetrics.startConnectionTimer();
            connectionTimers.put(emitter, connectionTimer);
            
            // 연결 즉시 초기화 메시지 전송 (SSE 표준 형식)
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "connected",
                            "userId", userId.toString(),
                            "storeId", storeId.toString(),
                            "timestamp", System.currentTimeMillis()
                    )));
            
            // 즉시 heartbeat 전송 (연결 확인용)
            emitter.send(SseEmitter.event()
                    .name("HEARTBEAT")
                    .data(Map.of(
                            "type", "initial_heartbeat",
                            "timestamp", System.currentTimeMillis()
                    )));
            
            log.info("SSE connection created and initialized for userId: {}, storeId: {}", userId, storeId);
            
        } catch (Exception e) {
            log.error("Failed to send initial message for userId: {}, storeId: {}", userId, storeId, e);
            sseMetrics.recordConnectionError("initialization_failed");
            emitter.completeWithError(e);
            return emitter;
        }

        emitter.onCompletion(() -> {
            removeEmitter(emitter);
            log.info("SSE connection completed for userId: {}, storeId: {}", userId, storeId);
        });
        
        emitter.onTimeout(() -> {
            removeEmitter(emitter);
            log.info("SSE connection timeout for userId: {}, storeId: {}", userId, storeId);
        });
        
        emitter.onError((throwable) -> {
            removeEmitter(emitter);
            log.error("SSE connection error for userId: {}, storeId: {}", userId, storeId, throwable);
        });

        return emitter;
    }
    
    private String createConnectionKey(Long userId, Long storeId) {
        return userId + ":" + storeId;
    }
    
    private void removeEmitter(SseEmitter emitter) {
        String connectionKey = emitterToConnectionKey.remove(emitter);
        if (connectionKey != null) {
            Set<SseEmitter> emitters = connectionEmitters.get(connectionKey);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    connectionEmitters.remove(connectionKey);
                }
            }
        }
        
        Timer.Sample timer = connectionTimers.remove(emitter);
        if (timer != null) {
            sseMetrics.stopConnectionTimer(timer);
        }
        sseMetrics.recordDisconnection();
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
        String userId = eventData.getUserId().toString();
        String storeId = eventData.getStoreId() != null ? eventData.getStoreId().toString() : "";
        String notificationType = eventData.getNotificationType();
        
        // 메트릭: 알림 전송 기록
        sseMetrics.recordNotificationSent(notificationType);
        Timer.Sample notificationTimer = sseMetrics.startNotificationTimer();
        
        // userId와 storeId를 기반으로 대상 연결들 찾기
        String targetConnectionKey = createConnectionKey(eventData.getUserId(), eventData.getStoreId());
        Set<SseEmitter> emitters = connectionEmitters.get(targetConnectionKey);
        
        if (emitters != null && !emitters.isEmpty()) {
            Map<String, Object> notificationData = Map.of(
                    "userId", userId,
                    "storeId", storeId,
                    "messageType", notificationType,
                    "title", eventData.getTitle(),
                    "message", eventData.getMessage(),
                    "messageData", eventData,
                    "timestamp", System.currentTimeMillis()
            );
            
            // 실패한 연결들을 수집할 리스트
            java.util.List<SseEmitter> failedEmitters = new java.util.ArrayList<>();
            int successCount = 0;
            
            for (SseEmitter emitter : emitters) {
                // 안전한 전송 사용
                boolean sendSuccess = safeSend(emitter, SseEmitter.event()
                        .name("ORDER_NOTIFICATION")
                        .data(notificationData));
                
                if (sendSuccess) {
                    successCount++;
                } else {
                    failedEmitters.add(emitter);
                }
            }
            
            // 실패한 연결들 정리
            failedEmitters.forEach(this::removeEmitter);
            
            if (successCount > 0) {
                // 메트릭: 알림 전송 성공
                sseMetrics.recordNotificationSuccess(notificationType);
                sseMetrics.stopNotificationTimer(notificationTimer);
                
                log.info("Order notification sent to userId: {}, type: {}, success: {}/{}", 
                        userId, notificationType, successCount, emitters.size());
            } else {
                // 메트릭: 알림 전송 실패
                sseMetrics.recordNotificationError(notificationType, "all_connections_failed");
                sseMetrics.stopNotificationTimer(notificationTimer);
                
                log.debug("Failed to send order notification to userId: {} (all connections failed)", userId);
            }
        } else {
            // 메트릭: 연결 없음
            sseMetrics.recordNotificationError(notificationType, "no_connection");
            sseMetrics.stopNotificationTimer(notificationTimer);
            
            log.warn("No SSE connection found for userId: {}", userId);
        }
    }

    /**
     * 모든 활성 연결에 heartbeat 메시지를 전송하여 연결 유지
     */
    private void sendHeartbeat() {
        if (connectionEmitters.isEmpty()) {
            return;
        }
        
        int totalConnections = connectionEmitters.values().stream().mapToInt(Set::size).sum();
        log.debug("Sending heartbeat to {} active connections", totalConnections);
        
        // 메트릭: Heartbeat 시작
        Timer.Sample heartbeatTimer = sseMetrics.startHeartbeatTimer();
        
        // 실패한 연결들을 저장할 리스트
        java.util.List<SseEmitter> failedEmitters = new java.util.ArrayList<>();
        int successCount = 0;
        
        for (Map.Entry<String, Set<SseEmitter>> entry : connectionEmitters.entrySet()) {
            String connectionKey = entry.getKey();
            Set<SseEmitter> emitters = entry.getValue();
            
            for (SseEmitter emitter : emitters) {
                sseMetrics.recordHeartbeatSent();
                
                // 안전한 전송 사용
                boolean sendSuccess = safeSend(emitter, SseEmitter.event()
                        .name("HEARTBEAT")
                        .data(Map.of(
                                "type", "heartbeat",
                                "timestamp", System.currentTimeMillis()
                        )));
                
                if (sendSuccess) {
                    sseMetrics.recordHeartbeatSuccess();
                    successCount++;
                    log.trace("Heartbeat sent to connection: {}", connectionKey);
                } else {
                    sseMetrics.recordHeartbeatFailure();
                    log.debug("Failed to send heartbeat to connection: {}, connection will be removed", connectionKey);
                    failedEmitters.add(emitter);
                }
            }
        }
        
        // 실패한 연결들을 제거
        failedEmitters.forEach(this::removeEmitter);
        
        // 메트릭: Heartbeat 완료
        sseMetrics.stopHeartbeatTimer(heartbeatTimer);
        
        // 연결 수 동기화
        int currentConnections = connectionEmitters.values().stream().mapToInt(Set::size).sum();
        sseMetrics.setCurrentConnections(currentConnections);
        
        if (!failedEmitters.isEmpty()) {
            log.info("Heartbeat completed: {} success, {} failed, {} total connections", 
                     successCount, failedEmitters.size(), currentConnections);
        }
    }

    /**
     * 연결 수 조회
     */
    public int getActiveConnectionCount() {
        return connectionEmitters.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * 특정 사용자의 연결 상태 확인
     */
    public boolean hasActiveConnection(Long userId, Long storeId) {
        String connectionKey = createConnectionKey(userId, storeId);
        Set<SseEmitter> emitters = connectionEmitters.get(connectionKey);
        return emitters != null && !emitters.isEmpty();
    }
    
    /**
     * 안전한 SSE 전송 메서드 - 연결이 끊어진 경우 예외를 조용히 처리
     */
    private boolean safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (AsyncRequestNotUsableException e) {
            log.debug("SSE connection no longer usable: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.debug("SSE IO error (client disconnect): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected SSE error: {}", e.getMessage(), e);
            return false;
        }
    }
}
