package com.example.hotelmanagement.dto.shiftassignment;

import com.example.hotelmanagement.entity.enums.AssignmentStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ShiftAssignmentResponse(
        String publicId,
        String employeeCode,
        String staffName,
        String shiftCode,
        String shiftName,
        LocalDate workDate,
        OffsetDateTime shiftStartAt,
        OffsetDateTime shiftEndAt,
        AssignmentStatus status,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
