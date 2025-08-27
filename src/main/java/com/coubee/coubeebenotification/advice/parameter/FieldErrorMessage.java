package com.coubee.coubeebenotification.advice.parameter;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class FieldErrorMessage {
    private String field;
    private String message;

    @Builder
    public FieldErrorMessage(String field, String message) {
        this.field = field;
        this.message = message;
    }
}

