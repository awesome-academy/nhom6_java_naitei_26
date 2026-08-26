package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StaffListResponse", description = "Minimal Staff data for scheduling and selection UIs.")
public record StaffListResponse(
        @Schema(description = "Employee code", example = "EMP-0001")
        String employeeCode,

        @Schema(description = "Staff full name", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "Position", example = "Receptionist")
        String position,

        @Schema(description = "Department", nullable = true)
        String department,

        @Schema(description = "Employment status")
        EmploymentStatus employmentStatus
) {
}
