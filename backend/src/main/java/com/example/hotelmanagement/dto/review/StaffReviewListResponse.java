package com.example.hotelmanagement.dto.review;

import java.util.List;

public record StaffReviewListResponse(
        List<StaffReviewResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
