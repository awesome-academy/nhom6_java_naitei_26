package com.example.hotelmanagement.dto.review;

import java.util.List;

public record PublishedReviewListResponse(
        List<PublishedReviewResponse> items,
        PublishedReviewSummaryResponse summary,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
