package com.coubee.coubeebenotification.api.open;

import com.coubee.coubeebenotification.common.web.context.GatewayRequestHeaderUtils;
import com.coubee.coubeebenotification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(HttpServletResponse response) {
        try {
            Long userId = GatewayRequestHeaderUtils.getUserIdOrThrowException();
            String storeIdHeader = GatewayRequestHeaderUtils.getStoreIdOrThrowException();
            log.info("Got userId: {}, storeId header: '{}'", userId, storeIdHeader);

            Long storeId;
            try {
                storeId = Long.parseLong(storeIdHeader.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid X-Store-Id header: " + storeIdHeader);
            }

            log.info("SSE connection request for userId: {}, storeId: {} (from header)", userId, storeId);
        
        // SSE 연결을 위한 HTTP 헤더 설정
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no"); // nginx buffering 방지
        
        // 기존 연결이 있다면 정리하고 새 연결 생성
        if (notificationService.hasActiveConnection(userId, storeId)) {
            log.info("Replacing existing SSE connection for userId: {}, storeId: {}", userId, storeId);
        }
        
            SseEmitter emitter = notificationService.createConnection(userId, storeId);
            
            log.info("SSE connection established for userId: {}, storeId: {}", userId, storeId);
            return emitter;
            
        } catch (Exception e) {
            log.error("Error creating SSE connection", e);
            // 500 에러 대신 빈 emitter 반환
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Connection failed: " + e.getMessage()));
                errorEmitter.complete();
            } catch (Exception sendError) {
                log.error("Failed to send error message", sendError);
            }
            return errorEmitter;
        }
    }

    @GetMapping(value = "/status/{userId}")
    public Map<String, Object> getConnectionStatus(@PathVariable Long userId, 
                                                  @RequestHeader(value = "X-Store-Id", required = false) String storeIdHeader) {
        if (storeIdHeader == null || storeIdHeader.trim().isEmpty()) {
            throw new IllegalArgumentException("X-Store-Id header is required");
        }
        
        Long storeId;
        try {
            storeId = Long.parseLong(storeIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid X-Store-Id header: " + storeIdHeader);
        }
        boolean hasConnection = notificationService.hasActiveConnection(userId, storeId);
        int totalConnections = notificationService.getActiveConnectionCount();
        
        return Map.of(
                "userId", userId,
                "storeId", storeId,
                "hasActiveConnection", hasConnection,
                "totalActiveConnections", totalConnections,
                "timestamp", System.currentTimeMillis()
        );
    }

//    @PostMapping("/test/{userId}")
//    public ResponseEntity<String> sendTestNotification(@PathVariable Long userId, @RequestBody String message) {
//        log.info("Sending test notification to userId: {}, message: {}", userId, message);
//
//        notificationService.sendNotification(userId, "TEST", "Test Notification", message);
//
//        return ResponseEntity.ok("Test notification sent");
//    }
}
