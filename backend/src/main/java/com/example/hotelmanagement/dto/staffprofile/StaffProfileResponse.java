package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(name = "StaffProfileResponse", description = "Staff profile data. Only ADMIN can view this resource, so baseSalary is always visible to the caller.")
public record StaffProfileResponse(
    @Schema(description = "Employee code", example = "EMP-0001")
    String employeeCode,

    @Schema(description = "Public UUID of the owning user")
    String userPublicId,

    @Schema(description = "Owner full name", example = "Nguyen Van A")
    String fullName,

    @Schema(description = "Owner email", example = "staff@example.com")
    String email,

    @Schema(description = "Position", example = "Receptionist")
    String position,

    @Schema(description = "Department", nullable = true)
    String department,

    @Schema(description = "Hire date")
    LocalDate hiredAt,

    @Schema(description = "Termination date", nullable = true)
    LocalDate terminatedAt,

    @Schema(description = "Employment status")
    EmploymentStatus employmentStatus,

    @Schema(description = "Base salary. Admin-only field.", nullable = true)
    BigDecimal baseSalary,

    @Schema(description = "Created timestamp")
    OffsetDateTime createdAt,

    @Schema(description = "Updated timestamp")
    OffsetDateTime updatedAt
) {
}
