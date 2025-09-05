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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SseMetrics sseMetrics;
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final Map<String, Timer.Sample> connectionTimers = new ConcurrentHashMap<>();
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

    public SseEmitter createConnection(Long userId) {
        String userIdStr = userId.toString();
        
        // 메트릭: 연결 시도 기록
        sseMetrics.recordConnectionAttempt();
        
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
            
            // 기존 연결 정리
            Timer.Sample oldTimer = connectionTimers.remove(userIdStr);
            if (oldTimer != null) {
                sseMetrics.stopConnectionTimer(oldTimer);
            }
            emitterMap.remove(userIdStr);
            sseMetrics.recordDisconnection();
            log.info("Removed existing SSE connection for userId: {}", userId);
        }
        
        SseEmitter emitter = new SseEmitter(0L); // 무한 타임아웃 (heartbeat으로 관리)
        
        try {
            // Cloudflare 버퍼링 우회를 위한 초기 데이터 전송
            StringBuilder padding = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                padding.append(" "); // 공백으로 패딩 추가
            }
            
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "connected",
                            "userId", userIdStr,
                            "timestamp", System.currentTimeMillis(),
                            "padding", padding.toString() // Cloudflare 버퍼링 우회용
                    )));
            
            // 추가 flush 데이터 (Cloudflare 즉시 전송 보장)
            emitter.send(SseEmitter.event()
                    .name("FLUSH")
                    .data(" ".repeat(100))); // 100자 패딩
            
            // 성공한 연결만 맵에 저장
            emitterMap.put(userIdStr, emitter);
            
            // 메트릭: 연결 성공 및 타이머 시작
            sseMetrics.recordConnectionSuccess();
            Timer.Sample connectionTimer = sseMetrics.startConnectionTimer();
            connectionTimers.put(userIdStr, connectionTimer);
            
            log.info("SSE connection created and initialized for userId: {}", userId);
            
        } catch (IOException e) {
            log.error("Failed to send initial message for userId: {}", userId, e);
            sseMetrics.recordConnectionError("initialization_failed");
            emitter.completeWithError(e);
            return emitter;
        }

        emitter.onCompletion(() -> {
            emitterMap.remove(userId.toString());
            Timer.Sample timer = connectionTimers.remove(userId.toString());
            if (timer != null) {
                sseMetrics.stopConnectionTimer(timer);
            }
            sseMetrics.recordDisconnection();
            log.info("SSE connection completed for userId: {}", userId);
        });
        
        emitter.onTimeout(() -> {
            emitterMap.remove(userId.toString());
            Timer.Sample timer = connectionTimers.remove(userId.toString());
            if (timer != null) {
                sseMetrics.stopConnectionTimer(timer);
            }
            sseMetrics.recordDisconnection();
            log.info("SSE connection timeout for userId: {}", userId);
        });
        
        emitter.onError((throwable) -> {
            emitterMap.remove(userId.toString());
            Timer.Sample timer = connectionTimers.remove(userId.toString());
            if (timer != null) {
                sseMetrics.stopConnectionTimer(timer);
            }
            sseMetrics.recordDisconnection();
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
        String userId = eventData.getUserId().toString();
        String storeId = eventData.getStoreId() != null ? eventData.getStoreId().toString() : "";
        String notificationType = eventData.getNotificationType();
        
        // 메트릭: 알림 전송 기록
        sseMetrics.recordNotificationSent(notificationType);
        Timer.Sample notificationTimer = sseMetrics.startNotificationTimer();
        
        SseEmitter emitter = emitterMap.get(userId);
        
        if (emitter != null) {
            try {
                Map<String, Object> notificationData = Map.of(
                        "userId", userId,
                        "storeId", storeId,
                        "messageType", notificationType,
                        "title", eventData.getTitle(),
                        "message", eventData.getMessage(),
                        "messageData", eventData,
                        "timestamp", System.currentTimeMillis()
                );

                emitter.send(SseEmitter.event()
                        .name("ORDER_NOTIFICATION")
                        .data(notificationData));

                // 메트릭: 알림 전송 성공
                sseMetrics.recordNotificationSuccess(notificationType);
                sseMetrics.stopNotificationTimer(notificationTimer);
                
                log.info("Order notification sent to userId: {}, type: {}", userId, notificationType);
                
            } catch (IOException e) {
                // 메트릭: 알림 전송 실패
                sseMetrics.recordNotificationError(notificationType, "send_failed");
                sseMetrics.stopNotificationTimer(notificationTimer);
                
                log.error("Failed to send order notification to userId: {}", userId, e);
                
                // 연결 정리
                Timer.Sample connectionTimer = connectionTimers.remove(userId);
                if (connectionTimer != null) {
                    sseMetrics.stopConnectionTimer(connectionTimer);
                }
                emitterMap.remove(userId);
                sseMetrics.recordDisconnection();
                emitter.completeWithError(e);
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
        if (emitterMap.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} active connections", emitterMap.size());
        
        // 메트릭: Heartbeat 시작
        Timer.Sample heartbeatTimer = sseMetrics.startHeartbeatTimer();
        
        // 실패한 연결들을 저장할 리스트
        java.util.List<String> failedConnections = new java.util.ArrayList<>();
        int successCount = 0;
        
        for (Map.Entry<String, SseEmitter> entry : emitterMap.entrySet()) {
            String userId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            
            try {
                sseMetrics.recordHeartbeatSent();
                
                emitter.send(SseEmitter.event()
                        .name("HEARTBEAT")
                        .data(Map.of(
                                "type", "heartbeat",
                                "timestamp", System.currentTimeMillis()
                        )));
                
                sseMetrics.recordHeartbeatSuccess();
                successCount++;
                log.trace("Heartbeat sent to userId: {}", userId);
                
            } catch (IOException e) {
                sseMetrics.recordHeartbeatFailure();
                log.warn("Failed to send heartbeat to userId: {}, connection will be removed", userId, e);
                failedConnections.add(userId);
            }
        }
        
        // 실패한 연결들을 제거
        failedConnections.forEach(userId -> {
            SseEmitter emitter = emitterMap.remove(userId);
            Timer.Sample connectionTimer = connectionTimers.remove(userId);
            
            if (connectionTimer != null) {
                sseMetrics.stopConnectionTimer(connectionTimer);
            }
            
            if (emitter != null) {
                emitter.completeWithError(new IOException("Heartbeat failed"));
            }
            
            sseMetrics.recordDisconnection();
        });
        
        // 메트릭: Heartbeat 완료
        sseMetrics.stopHeartbeatTimer(heartbeatTimer);
        
        // 연결 수 동기화
        sseMetrics.setCurrentConnections(emitterMap.size());
        
        if (!failedConnections.isEmpty()) {
            log.info("Heartbeat completed: {} success, {} failed, {} total connections", 
                     successCount, failedConnections.size(), emitterMap.size());
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
