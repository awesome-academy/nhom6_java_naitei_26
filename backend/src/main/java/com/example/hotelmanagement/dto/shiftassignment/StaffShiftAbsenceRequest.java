package com.example.hotelmanagement.dto.shiftassignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffShiftAbsenceRequest(
        @NotBlank
        @Size(max = 10_000)
        String note
) {
}
