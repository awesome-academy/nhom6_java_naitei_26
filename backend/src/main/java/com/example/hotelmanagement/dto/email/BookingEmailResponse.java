package com.example.hotelmanagement.dto.email;

import com.example.hotelmanagement.entity.enums.EmailStatus;

import java.time.OffsetDateTime;

public record BookingEmailResponse(
        Long id,
        String toEmail,
        String subject,
        String body,
        EmailStatus status,
        Integer attemptCount,
        OffsetDateTime scheduledAt,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt
) {
}
