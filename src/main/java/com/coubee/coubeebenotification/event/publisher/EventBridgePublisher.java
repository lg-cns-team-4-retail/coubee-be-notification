package com.coubee.coubeebenotification.event.publisher;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBridgePublisher {
    
    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    
    @Value("${aws.eventbridge.bus-name:notification_bus}")
    private String eventBusName;
    
    @Value("${aws.eventbridge.source:coubee.notification}")
    private String eventSource;

    public void publishEvent(String eventType, Object eventData) {
        try {
            String eventDetail = objectMapper.writeValueAsString(eventData);
            
            PutEventsRequestEntry eventEntry = PutEventsRequestEntry.builder()
                    .source(eventSource)
                    .detailType(eventType)
                    .detail(eventDetail)
                    .eventBusName(eventBusName)
                    .time(Instant.now())
                    .build();

            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(eventEntry)
                    .build();

            PutEventsResponse response = eventBridgeClient.putEvents(request);
            
            if (response.failedEntryCount() > 0) {
                log.error("Failed to publish {} events to EventBridge", response.failedEntryCount());
                response.entries().forEach(entry -> {
                    if (entry.errorCode() != null) {
                        log.error("Event failed: {} - {}", entry.errorCode(), entry.errorMessage());
                    }
                });
            } else {
                log.info("Successfully published event to EventBridge: eventType={}, eventBus={}", 
                        eventType, eventBusName);
            }
            
        } catch (Exception e) {
            log.error("Error publishing event to EventBridge: eventType={}, error={}", 
                    eventType, e.getMessage(), e);
            throw new RuntimeException("Failed to publish event to EventBridge", e);
        }
    }

    public void publishOrderStatusUpdateEvent(OrderStatusUpdateEvent orderEvent) {
        // OrderStatusUpdateEvent에서 실제 데이터 추출
        Map<String, Object> notificationData = Map.of(
                "messageId", orderEvent.getEventId(),
                "userId", orderEvent.getUserId(),
                "messageType", orderEvent.getNotificationType(),
                "title", orderEvent.getTitle(),
                "message", orderEvent.getMessage(),
                "messageData", orderEvent // 받은 데이터를 messageData에 그대로 포함
        );
        
        publishEvent("notification_send", notificationData);
    }
}