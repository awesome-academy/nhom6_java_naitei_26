package com.example.hotelmanagement.dto.booking;

import java.util.List;

/**
 * Paginated response for booking list.
 */
public record BookingListResponse(
        List<BookingListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        BookingStats stats
) {

    /**
     * Summary statistics for the booking dashboard.
     */
    public record BookingStats(
            long total,
            long pending,
            long confirmed,
            long checkedIn,
            long checkedOut,
            long cancelled
    ) {}
}
