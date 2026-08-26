package com.example.hotelmanagement.dto.staffprofile;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import jakarta.validation.constraints.NotNull;

public record StaffEmploymentStatusUpdateRequest(
        @NotNull EmploymentStatus employmentStatus
) {
}
