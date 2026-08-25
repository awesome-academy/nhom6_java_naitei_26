package com.example.hotelmanagement.dto.payment;

import java.util.List;

public record PaymentListResponse(
        List<PaymentListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
