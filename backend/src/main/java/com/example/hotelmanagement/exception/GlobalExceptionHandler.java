package com.example.hotelmanagement.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MAX_LOG_VALUE_LENGTH = 200;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        logger.warn("Resource was not found: method={}, path={}",
                request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return createProblemDetail(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(
            DuplicateResourceException exception,
            HttpServletRequest request
    ) {
        logger.warn("Duplicate resource request: method={}, path={}",
                request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return createProblemDetail(HttpStatus.CONFLICT, "Duplicate resource", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ProblemDetail handleBusinessValidation(
            BusinessValidationException exception,
            HttpServletRequest request
    ) {
        logger.warn("Business validation failed: method={}, path={}",
                request.getMethod(), sanitizeForLog(request.getRequestURI()));
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Business validation failed",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                validationErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid",
                request
        );
        problemDetail.setProperty("errors", validationErrors);
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        logger.warn("Unreadable request body: method={}, path={}",
                request.getMethod(), sanitizeForLog(request.getRequestURI()), exception);
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "Request body is malformed or contains unsupported values",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        logger.warn("Database constraint rejected request: method={}, path={}",
                request.getMethod(), sanitizeForLog(request.getRequestURI()), exception);
        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Data conflict",
                "The request conflicts with existing data",
                request
        );
    }

    private ProblemDetail createProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));
        return problemDetail;
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
