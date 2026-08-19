package com.example.hotelmanagement.dto.shiftassignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ShiftAssignmentCreateRequest(
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String employeeCode,

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String shiftCode,

        @NotNull LocalDate workDate,
        @Size(max = 10_000) String note
) {
}
