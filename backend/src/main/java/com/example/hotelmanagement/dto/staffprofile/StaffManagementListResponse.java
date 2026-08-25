package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "StaffManagementListResponse", description = "Staff data for administrative account management. Salary is intentionally excluded.")
public record StaffManagementListResponse(
        String employeeCode,
        String fullName,
        String email,
        String position,
        String department,
        EmploymentStatus employmentStatus,
        UserStatus accountStatus,
        OffsetDateTime emailVerifiedAt,
        LocalDate hiredAt,
        LocalDate terminatedAt
) {
}
