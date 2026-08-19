package com.example.hotelmanagement.dto.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ShiftCreateRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String code,

        @NotBlank @Size(max = 80) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Boolean crossesMidnight,
        Boolean isActive
) {
}
