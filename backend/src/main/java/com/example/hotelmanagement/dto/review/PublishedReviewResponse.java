package com.example.hotelmanagement.dto.review;

import java.time.OffsetDateTime;

/**
 * Public-safe representation of a published guest review.
 *
 * <p>Booking identifiers, contact information, moderation metadata, and the internal staff actor
 * identifier are deliberately not part of this DTO.</p>
 */
public record PublishedReviewResponse(
        String customerName,
        String roomTypeName,
        Integer overallRating,
        Integer roomRating,
        Integer cleanlinessRating,
        Integer serviceRating,
        Integer valueRating,
        String title,
        String comment,
        String staffReply,
        OffsetDateTime staffRepliedAt,
        OffsetDateTime createdAt
) {
}
