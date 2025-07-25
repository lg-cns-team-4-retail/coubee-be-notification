package com.coubee.coubeebenotification.event.consumer;

import com.coubee.coubeebenotification.event.consumer.message.user.SiteUserInfoEvent;
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
    @KafkaListener(
            topics = SiteUserInfoEvent.Topic,
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE
                            + ":com.coubee.coubeebenotification.event.consumer.message.user.SiteUserInfoEvent"
            }
    )
    void handleSiteUserInfoEvent(SiteUserInfoEvent event, Acknowledgment ack) {
        log.info("SiteUserInfoEvent 처리. userId={}", event.getUsername());
        ack.acknowledge();
    }
}
