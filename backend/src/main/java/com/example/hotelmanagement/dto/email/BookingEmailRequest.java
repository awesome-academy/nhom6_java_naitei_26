package com.example.hotelmanagement.dto.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingEmailRequest(
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 10_000) String body
) {
}
