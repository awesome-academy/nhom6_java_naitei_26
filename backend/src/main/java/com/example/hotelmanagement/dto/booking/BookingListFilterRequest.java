package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Set;

/**
 * Filter parameters for booking list queries (Staff view).
 */
@Builder
public record BookingListFilterRequest(
        Set<BookingStatus> statuses,
        LocalDate checkInFrom,
        LocalDate checkInTo,
        LocalDate checkOutFrom,
        LocalDate checkOutTo,
        String sourceCode,
        String search,
        Integer page,
        Integer size
) {
    public BookingListFilterRequest {
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
    }

    public static BookingListFilterRequest empty() {
        return new BookingListFilterRequest(null, null, null, null, null, null, null, 0, 20);
    }
}
