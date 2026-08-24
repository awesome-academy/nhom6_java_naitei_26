package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "BookingCreateRequest", description = "Payload to create a new booking for the current customer")
public record BookingCreateRequest(
        @Schema(description = "Overrides the customer's default full name for this booking", nullable = true)
        @Size(max = 150)
        String contactName,

        @Schema(nullable = true)
        @Email
        @Size(max = 255)
        String contactEmail,

        @Schema(nullable = true)
        @Size(max = 20)
        @Pattern(regexp = "^[0-9+() .-]*$")
        String contactPhone,

        @Schema(nullable = true)
        @Size(max = 2000)
        String specialRequests,

        @NotEmpty
        @Valid
        List<BookingRoomCreateItem> rooms
) {
}
