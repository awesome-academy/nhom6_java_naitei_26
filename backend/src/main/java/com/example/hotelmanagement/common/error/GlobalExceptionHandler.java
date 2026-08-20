package com.example.hotelmanagement.common.error;

import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.RateOverrideConflictException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.RoomStatusConflictException;
import com.example.hotelmanagement.exceptions.ShiftOverlapException;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MAX_LOG_VALUE_LENGTH = 200;

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
        AuthException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
        ResourceNotFoundException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        log.warn("Resource was not found method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(
        DuplicateResourceException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        log.warn("Duplicate resource request method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(ShiftOverlapException.class)
    public ResponseEntity<ApiErrorResponse> handleShiftOverlap(
        ShiftOverlapException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        log.warn("Shift assignment overlap method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(RoomStatusConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleRoomStatusConflict(
        RoomStatusConflictException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        log.warn("Room status conflict method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessValidation(
        BusinessValidationException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        log.warn("Business validation failed method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(toError(status, exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageUnavailable(
        StorageUnavailableException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        log.error("Object storage request failed method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()), exception);
        return ResponseEntity.status(status).body(
            toError(status, "Image storage is temporarily unavailable", request, Map.of())
        );
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        log.warn("Unreadable request body method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(
            toError(status, "Request body is malformed or contains unsupported values", request, Map.of())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        log.warn("Invalid request parameter method={} path={} parameter={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()),
            sanitizeForLog(exception.getName()));
        return ResponseEntity.status(status).body(
            toError(status, "Request parameter has an unsupported value", request, Map.of())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        log.warn("Missing request parameter method={} path={} parameter={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()),
            sanitizeForLog(exception.getParameterName()));
        return ResponseEntity.status(status).body(
            toError(status, "A required request parameter is missing", request, Map.of())
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        log.warn("Database constraint rejected request method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()), exception);
        return ResponseEntity.status(status).body(
            toError(status, "The request conflicts with existing data", request, Map.of())
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        log.warn("Access denied method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return ResponseEntity.status(status).body(
            toError(status, "Bạn không có quyền thực hiện thao tác này", request, Map.of())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unexpected request failure method={} path={}",
            request.getMethod(), sanitizeForLog(request.getRequestURI()), exception);
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

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
            ? sanitized
            : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }
}
