package com.coubee.coubeebenotification.api.open;

import com.coubee.coubeebenotification.metrics.SseMetrics;
import com.coubee.coubeebenotification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final SseMetrics sseMetrics;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @GetMapping("/sse/summary")
    public Map<String, Object> getSseSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("activeConnections", notificationService.getActiveConnectionCount());
        summary.put("totalConnectionAttempts", getCounterValue("sse_connections_total"));
        summary.put("successfulConnections", getCounterValue("sse_connections_success_total"));
        summary.put("connectionErrors", getCounterValue("sse_connections_error_total"));
        summary.put("disconnections", getCounterValue("sse_disconnections_total"));
        summary.put("heartbeatsSent", getCounterValue("sse_heartbeats_sent_total"));
        summary.put("heartbeatsSuccess", getCounterValue("sse_heartbeats_success_total"));
        summary.put("heartbeatsFailure", getCounterValue("sse_heartbeats_failure_total"));
        summary.put("notificationsSent", getCounterValue("sse_notifications_sent_total"));
        summary.put("notificationsSuccess", getCounterValue("sse_notifications_success_total"));
        summary.put("notificationsError", getCounterValue("sse_notifications_error_total"));
        summary.put("timestamp", System.currentTimeMillis());
        return summary;
    }

    @GetMapping("/sse/health")
    public Map<String, Object> getSseHealthStatus() {
        int activeConnections = notificationService.getActiveConnectionCount();
        double successRate = calculateSuccessRate();
        boolean isHealthy = activeConnections >= 0 && successRate >= 0.95; // 95% 이상 성공률

        return Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "activeConnections", activeConnections,
                "successRate", successRate,
                "lastHeartbeatsSent", getCounterValue("sse_heartbeats_sent_total"),
                "lastNotificationsSent", getCounterValue("sse_notifications_sent_total"),
                "timestamp", System.currentTimeMillis()
        );
    }

    private double getCounterValue(String counterName) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find(counterName).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double calculateSuccessRate() {
        double totalConnections = getCounterValue("sse_connections_total");
        double successfulConnections = getCounterValue("sse_connections_success_total");
        
        if (totalConnections == 0) {
            return 1.0; // 연결 시도가 없으면 100%로 간주
        }
        
        return successfulConnections / totalConnections;
    }
}