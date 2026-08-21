package com.example.hotelmanagement.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingRoomChangeRequest(
        @NotBlank
        @Size(max = 20)
        String newRoomNumber,

        @NotNull
        LocalDate moveDate
) {
}
