package com.coubee.coubeebenotification.service;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationService {
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter createConnection(Long userId) {
        SseEmitter emitter = new SseEmitter(60 * 1000L * 30); // 30분 타임아웃
        emitterMap.put(userId.toString(), emitter);

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
}
