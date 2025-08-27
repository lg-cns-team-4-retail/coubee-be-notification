package com.coubee.coubeebenotification.event.consumer;

import com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent;
import com.coubee.coubeebenotification.event.publisher.EventBridgePublisher;
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

    @KafkaListener(
            topics = OrderStatusUpdateEvent.Topic,
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE
                            + ":com.coubee.coubeebenotification.event.consumer.message.order.OrderStatusUpdateEvent"
            }
    )
    void handleOrderStatusUpdateEvent(OrderStatusUpdateEvent event, Acknowledgment ack) {
        eventBridgePublisher.publishOrderStatusUpdateEvent(event);
            
        ack.acknowledge();
    }
}
