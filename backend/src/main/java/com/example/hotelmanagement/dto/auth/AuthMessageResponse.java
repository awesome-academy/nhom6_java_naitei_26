package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthMessageResponse", description = "Simple authentication workflow response")
public record AuthMessageResponse(
    @Schema(description = "Safe user-facing message", example = "Request accepted")
    String message
) {
}
