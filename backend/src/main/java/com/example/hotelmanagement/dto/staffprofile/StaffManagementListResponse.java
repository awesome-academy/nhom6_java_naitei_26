package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(name = "StaffManagementListResponse", description = "Staff data for administrative account management.")
public record StaffManagementListResponse(
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String position,
        String department,
        EmploymentStatus employmentStatus,
        UserStatus accountStatus,
        OffsetDateTime emailVerifiedAt,
        LocalDate hiredAt,
        LocalDate terminatedAt,
        @Schema(description = "Base salary. Admin-only field.", nullable = true)
        BigDecimal baseSalary
) {
}
