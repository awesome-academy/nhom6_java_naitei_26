package com.example.hotelmanagement.dto.review;

public record PublishedReviewSummaryResponse(
        long totalReviews,
        Double averageOverallRating,
        Double averageRoomRating,
        Double averageCleanlinessRating,
        Double averageServiceRating,
        Double averageValueRating
) {
}
