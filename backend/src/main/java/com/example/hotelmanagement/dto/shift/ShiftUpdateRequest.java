package com.example.hotelmanagement.dto.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ShiftUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull Boolean crossesMidnight,
        Boolean isActive
) {
}
