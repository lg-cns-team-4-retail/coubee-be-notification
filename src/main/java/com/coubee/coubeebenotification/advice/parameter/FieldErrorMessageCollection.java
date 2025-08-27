package com.coubee.coubeebenotification.advice.parameter;

import lombok.Getter;
import lombok.ToString;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ToString
@Getter
public class FieldErrorMessageCollection {

    private List<FieldErrorMessage> errors = new ArrayList<>();

    FieldErrorMessageCollection(List<FieldError> fieldErrors, List<ObjectError> globalErrors) {
        errors.addAll(getFieldErrors(fieldErrors));
        errors.addAll(getObjectErrors(globalErrors));
    }

    public FieldErrorMessageCollection(List<FieldErrorMessage> errors) {
        this.errors = errors;
    }

    private List<FieldErrorMessage> getFieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(f -> FieldErrorMessage.builder()
                        .field(f.getField())
                        .message(f.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());
    }

    private List<FieldErrorMessage> getObjectErrors(List<ObjectError> globalErrors) {
        return globalErrors.stream()
                .map(o -> FieldErrorMessage.builder()
                        .field(o.getObjectName())
                        .message(o.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());
    }
}