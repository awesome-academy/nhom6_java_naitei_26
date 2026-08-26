package com.example.hotelmanagement.dto.review;

import com.example.hotelmanagement.entity.enums.ReviewStatus;

import java.time.OffsetDateTime;

public record ReviewResponse(
        Long id,
        String bookingPublicId,
        String bookingCode,
        String customerName,
        String customerEmail,
        String roomNumber,
        String roomTypeCode,
        String roomTypeName,
        Integer overallRating,
        Integer roomRating,
        Integer cleanlinessRating,
        Integer serviceRating,
        Integer valueRating,
        String title,
        String comment,
        ReviewStatus status,
        String moderationReason,
        String staffReply,
        Long staffReplyBy,
        OffsetDateTime staffRepliedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
