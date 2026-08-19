package com.example.hotelmanagement.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Schema(name = "ApiErrorResponse", description = "Standard error response")
public record ApiErrorResponse(
    @Schema(description = "Error timestamp")
    OffsetDateTime timestamp,

    @Schema(description = "HTTP status code", example = "400")
    int status,

    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    String error,

    @Schema(description = "Safe user-facing error message", example = "Dữ liệu yêu cầu không hợp lệ")
    String message,

    @Schema(description = "Request path", example = "/api/auth/login")
    String path,

    @Schema(description = "Validation errors keyed by field name")
    Map<String, String> fieldErrors
) {
}
