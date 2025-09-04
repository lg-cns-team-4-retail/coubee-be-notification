package com.coubee.coubeebenotification.event.consumer.message.order;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OrderStatusUpdateEvent {
    public static final String Topic = "notification-events";

    private String eventId;

    private String notificationType; // PAID,CANCELLED_USER,CANCELLED_ADMIN,PREPARING(주문수락),PREPARED(준비완료),RECEIVED(수령완료)

    private String orderId;

    private Long userId;

    private Long storeId;

    private String title;

    private String message;

    private LocalDateTime timestamp;
}
