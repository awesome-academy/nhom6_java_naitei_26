package com.example.hotelmanagement.dto.review;

import java.util.List;

public record ReviewListResponse(
        List<ReviewResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
