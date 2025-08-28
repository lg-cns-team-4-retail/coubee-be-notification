package com.coubee.coubeebenotification.api.open;

import com.coubee.coubeebenotification.common.web.context.GatewayRequestHeaderUtils;
import com.coubee.coubeebenotification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/subscribe")
    public SseEmitter subscribe() {
        Long userId = GatewayRequestHeaderUtils.getUserIdOrThrowException();

        log.info("SSE connection request for userId: {}", userId);
        
        SseEmitter emitter = notificationService.createConnection(userId);
        
        log.info("SSE connection established for userId: {}", userId);
        return emitter;
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
