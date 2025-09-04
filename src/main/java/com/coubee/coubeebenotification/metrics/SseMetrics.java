package com.coubee.coubeebenotification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Component
public class
SseMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // SSE 연결 관련 메트릭
    private final Counter sseConnectionsTotal;
    private final Counter sseConnectionsSuccess;
    private final Counter sseConnectionsError;
    private final Counter sseDisconnectionsTotal;
    private final AtomicLong currentConnections;
    
    // Heartbeat 관련 메트릭
    private final Counter heartbeatsSent;
    private final Counter heartbeatsSuccess;
    private final Counter heartbeatsFailure;
    private final Timer heartbeatDuration;
    
    // 알림 전송 관련 메트릭
    private final Counter notificationsSent;
    private final Counter notificationsSuccess;
    private final Counter notificationsError;
    private final Timer notificationDuration;
    
    // 연결 지속 시간 메트릭
    private final Timer connectionDuration;
    
    public SseMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.currentConnections = new AtomicLong(0);
        
        // SSE 연결 메트릭 초기화
        this.sseConnectionsTotal = Counter.builder("sse_connections_total")
                .description("Total number of SSE connection attempts")
                .register(meterRegistry);
                
        this.sseConnectionsSuccess = Counter.builder("sse_connections_success_total")
                .description("Total number of successful SSE connections")
                .register(meterRegistry);
                
        this.sseConnectionsError = Counter.builder("sse_connections_error_total")
                .description("Total number of failed SSE connections")
                .register(meterRegistry);
                
        this.sseDisconnectionsTotal = Counter.builder("sse_disconnections_total")
                .description("Total number of SSE disconnections")
                .register(meterRegistry);
        
        // 현재 활성 연결 수 게이지
        Gauge.builder("sse_active_connections", currentConnections, AtomicLong::doubleValue)
                .description("Current number of active SSE connections")
                .register(meterRegistry);
        
        // Heartbeat 메트릭 초기화
        this.heartbeatsSent = Counter.builder("sse_heartbeats_sent_total")
                .description("Total number of heartbeats sent")
                .register(meterRegistry);
                
        this.heartbeatsSuccess = Counter.builder("sse_heartbeats_success_total")
                .description("Total number of successful heartbeats")
                .register(meterRegistry);
                
        this.heartbeatsFailure = Counter.builder("sse_heartbeats_failure_total")
                .description("Total number of failed heartbeats")
                .register(meterRegistry);
                
        this.heartbeatDuration = Timer.builder("sse.heartbeat.duration")
                .description("Time taken to send heartbeat to all connections")
                .register(meterRegistry);
        
        // 알림 전송 메트릭 초기화
        this.notificationsSent = Counter.builder("sse_notifications_sent_total")
                .description("Total number of notifications sent")
                .register(meterRegistry);
                
        this.notificationsSuccess = Counter.builder("sse_notifications_success_total")
                .description("Total number of successful notifications")
                .register(meterRegistry);
                
        this.notificationsError = Counter.builder("sse_notifications_error_total")
                .description("Total number of failed notifications")
                .register(meterRegistry);
                
        this.notificationDuration = Timer.builder("sse.notification.duration")
                .description("Time taken to send a notification")
                .register(meterRegistry);
        
        // 연결 지속 시간 메트릭
        this.connectionDuration = Timer.builder("sse.connection.duration")
                .description("Duration of SSE connections")
                .register(meterRegistry);
    }
    
    // SSE 연결 메트릭 메서드
    public void recordConnectionAttempt() {
        sseConnectionsTotal.increment();
        log.debug("SSE connection attempt recorded");
    }
    
    public void recordConnectionSuccess() {
        sseConnectionsSuccess.increment();
        currentConnections.incrementAndGet();
        log.debug("SSE connection success recorded, current connections: {}", currentConnections.get());
    }
    
    public void recordConnectionError(String errorType) {
        sseConnectionsError.increment();
        log.debug("SSE connection error recorded: {}", errorType);
    }
    
    public void recordDisconnection() {
        sseDisconnectionsTotal.increment();
        currentConnections.decrementAndGet();
        log.debug("SSE disconnection recorded, current connections: {}", currentConnections.get());
    }
    
    // Heartbeat 메트릭 메서드
    public void recordHeartbeatSent() {
        heartbeatsSent.increment();
    }
    
    public void recordHeartbeatSuccess() {
        heartbeatsSuccess.increment();
    }
    
    public void recordHeartbeatFailure() {
        heartbeatsFailure.increment();
    }
    
    public Timer.Sample startHeartbeatTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopHeartbeatTimer(Timer.Sample sample) {
        sample.stop(heartbeatDuration);
    }
    
    // 알림 전송 메트릭 메서드
    public void recordNotificationSent(String notificationType) {
        notificationsSent.increment();
    }
    
    public void recordNotificationSuccess(String notificationType) {
        notificationsSuccess.increment();
    }
    
    public void recordNotificationError(String notificationType, String errorType) {
        notificationsError.increment();
    }
    
    public Timer.Sample startNotificationTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopNotificationTimer(Timer.Sample sample) {
        sample.stop(notificationDuration);
    }
    
    // 연결 지속 시간 메트릭
    public Timer.Sample startConnectionTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopConnectionTimer(Timer.Sample sample) {
        sample.stop(connectionDuration);
    }
    
    // 현재 활성 연결 수 반환
    public long getCurrentConnections() {
        return currentConnections.get();
    }
    
    // 연결 수 직접 설정 (동기화용)
    public void setCurrentConnections(int count) {
        currentConnections.set(count);
        log.debug("Current connections set to: {}", count);
    }
    
    // 추가 메트릭을 위한 MeterRegistry 노출
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}