package com.coubee.coubeebenotification.advice;


import com.coubee.coubeebenotification.advice.parameter.ParameterErrorDto;
import com.coubee.coubeebenotification.common.dto.ApiResponseDto;
import com.coubee.coubeebenotification.common.exception.BadParameter;
import com.coubee.coubeebenotification.common.exception.ClientError;
import com.coubee.coubeebenotification.common.exception.NotFound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@Order(value = 1)
@RestControllerAdvice
public class ApiCommonAdvice {
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({BadParameter.class})
    public ApiResponseDto<String> handleBadParameter(BadParameter e) {
        return ApiResponseDto.createError(
                e.getErrorCode(),
                e.getErrorMessage()
        );
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({NotFound.class})
    public ApiResponseDto<String> handleNotFound(NotFound e) {
        return ApiResponseDto.createError(
                e.getErrorCode(),
                e.getErrorMessage()
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({ClientError.class})
    public ApiResponseDto<String> handleClientError(ClientError e) {
        return ApiResponseDto.createError(
                e.getErrorCode(),
                e.getErrorMessage()
        );
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({NoResourceFoundException.class})
    public ApiResponseDto<String> handleNoResourceFoundException(NoResourceFoundException e) {
        return ApiResponseDto.createError(
                "NoResource",
                "리소스를 찾을 수 없습니다.");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ApiResponseDto<ParameterErrorDto.FieldList> handleArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        ParameterErrorDto.FieldList fieldList = ParameterErrorDto.FieldList.of(result);

        String errorMessage = fieldList.getErrorMessage();
        return ApiResponseDto.createError("ParameterNotValid", errorMessage, fieldList);
    }

    // SSE 관련 예외는 별도 처리 (ApiResponse 반환하지 않음)
    @ExceptionHandler({AsyncRequestNotUsableException.class})
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e, HttpServletRequest request) {
        // SSE 연결이 끊어진 경우 로그만 남기고 조용히 처리
        if (request.getRequestURI().contains("/api/notification/subscribe")) {
            log.debug("SSE connection closed by client: {}", e.getMessage());
        } else {
            log.warn("Async request not usable: {}", e.getMessage());
        }
        // 응답을 반환하지 않음 (이미 연결이 끊어진 상태)
    }
    
    @ExceptionHandler({IOException.class})
    public void handleIOException(IOException e, HttpServletRequest request) {
        // Broken pipe 등 IO 에러는 클라이언트 연결 끊어짐으로 처리
        if (request.getRequestURI().contains("/api/notification/subscribe")) {
            log.debug("SSE IO error (client disconnect): {}", e.getMessage());
        } else {
            log.warn("IO error: {}", e.getMessage());
        }
        // 응답을 반환하지 않음
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler({Exception.class})
    public ApiResponseDto<String> handleException(Exception e, HttpServletRequest request) {
        // SSE 경로에서는 예외 처리하지 않음
        if (request.getRequestURI().contains("/api/notification/subscribe")) {
            log.error("SSE error: {}", e.getMessage(), e);
            return null; // SSE는 ApiResponse 반환 불가
        }
        
        e.printStackTrace();
        return ApiResponseDto.createError(
                "ServerError",
                "서버 에러입니다.");
    }
}
