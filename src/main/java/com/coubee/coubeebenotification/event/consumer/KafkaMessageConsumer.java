package com.coubee.coubeebenotification.event.consumer;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import com.coubee.coubeebenotification.event.publisher.EventBridgePublisher;
import com.coubee.coubeebenotification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMessageConsumer {
    
    private final EventBridgePublisher eventBridgePublisher;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = OrderStatusUpdateEvent.Topic,
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE
                            + ":com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent"
            }
    )
    void handleOrderStatusUpdateEvent(OrderStatusUpdateEvent event, Acknowledgment ack) {
        try {
            log.info("Received Kafka event: {}", event);

            if(event.getNotificationType().equals("PAID") || event.getNotificationType().equals("NEW_ORDER") || event.getNotificationType().equals("CANCELLED_USER")) {
                // SSE를 통해 실시간 알림 전송
                notificationService.sendOrderNotification(event);
            } else {
                // EventBridge로 이벤트 전송
                eventBridgePublisher.publishOrderStatusUpdateEvent(event);
            }

            log.info("Successfully processed event: userId={}, messageType={}", 
                    event.getUserId(), event.getNotificationType());
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing Kafka event: {}", e.getMessage(), e);
            // 에러 발생 시 acknowledge하지 않아 재시도됨
            throw e;
        }
    }
}
