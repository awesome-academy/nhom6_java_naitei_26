package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "BookingCancelRequest", description = "Payload to cancel a booking")
public record BookingCancelRequest(
        @Schema(nullable = true)
        @Size(max = 2000)
        String reason
) {
}
