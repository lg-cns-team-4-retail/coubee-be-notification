package com.coubee.coubeebenotification.advice.parameter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ParameterErrorDto {
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Field {
        private String field;
        private String message;

        static Field of(FieldError error) {
            Field field = new Field();
            field.field = error.getField();
            field.message = error.getDefaultMessage();
            return field;
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FieldList {
        private List<Field> errors = new ArrayList<>();

        public static FieldList of(BindingResult result) {
            FieldList list = new FieldList();
            if (result == null || result.getFieldErrors() == null) {
                return list;
            }

            list.errors = result.getFieldErrors().stream().map(Field::of).collect(Collectors.toList());

            return list;
        }

        public String getErrorMessage() {
            StringBuilder builder = new StringBuilder();
            for (Field field : errors) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }

                builder.append(field.message);
            }

            return builder.toString();
        }
    }
}
