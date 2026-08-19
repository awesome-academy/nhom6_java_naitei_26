package com.example.hotelmanagement.common.error;

import com.example.hotelmanagement.exceptions.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
        AuthException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
            toError(status, "Dữ liệu yêu cầu không hợp lệ", request, fieldErrors)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unexpected request failure path={}", request.getRequestURI(), exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(
            toError(status, "Có lỗi hệ thống, vui lòng thử lại sau", request, Map.of())
        );
    }

    private ApiErrorResponse toError(
        HttpStatus status,
        String message,
        HttpServletRequest request,
        Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
            OffsetDateTime.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            fieldErrors
        );
    }
}
