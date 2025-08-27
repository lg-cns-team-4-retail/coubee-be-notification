package com.coubee.coubeebenotification.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NotificationEventDto {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("messageType")
    private String messageType;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("messageData")
    private Map<String, Object> messageData;
}