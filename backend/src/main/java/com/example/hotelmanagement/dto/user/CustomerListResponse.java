package com.example.hotelmanagement.dto.user;

import java.util.List;

public record CustomerListResponse(
        List<CustomerListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
