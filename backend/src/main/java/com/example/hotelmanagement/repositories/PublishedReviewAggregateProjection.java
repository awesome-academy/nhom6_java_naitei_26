package com.example.hotelmanagement.repositories;

/**
 * Aggregate values for the complete set of published reviews.
 *
 * <p>The nullable category averages are intentional: a category is optional on a review and
 * therefore has no average when no published review contains a value for it.</p>
 */
public interface PublishedReviewAggregateProjection {

    Long getTotalReviews();

    Double getAverageOverallRating();

    Double getAverageRoomRating();

    Double getAverageCleanlinessRating();

    Double getAverageServiceRating();

    Double getAverageValueRating();
}
