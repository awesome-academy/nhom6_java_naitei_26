package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.entity.enums.UserStatus;

import java.time.OffsetDateTime;

public record CustomerListItemResponse(
        String publicId,
        String fullName,
        String email,
        String phone,
        UserStatus status,
        long bookingCount,
        OffsetDateTime createdAt
) {
}
