package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CustomerStatusUpdateRequest", description = "Customer account status transition")
public record CustomerStatusUpdateRequest(
        @NotNull
        @Schema(description = "The only allowed target states are ACTIVE and DEACTIVATED")
        UserStatus status
) {
}
