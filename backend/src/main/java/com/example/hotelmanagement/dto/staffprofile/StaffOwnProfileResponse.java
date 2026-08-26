package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "StaffOwnProfileResponse", description = "Non-sensitive profile data for the authenticated staff member.")
public record StaffOwnProfileResponse(
        @Schema(description = "Internal employee code", example = "EMP-0001")
        String employeeCode,

        @Schema(description = "Staff member's full name", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "Staff member's login email", example = "staff@example.com")
        String email,

        @Schema(description = "Optional phone number", nullable = true)
        String phone,

        @Schema(description = "Optional avatar URL", nullable = true)
        String avatarUrl,

        @Schema(description = "Position", nullable = true)
        String position,

        @Schema(description = "Department", nullable = true)
        String department,

        @Schema(description = "Hire date")
        LocalDate hiredAt,

        @Schema(description = "Current employment status")
        EmploymentStatus employmentStatus
) {
}
