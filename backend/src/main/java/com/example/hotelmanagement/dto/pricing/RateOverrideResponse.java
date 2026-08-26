package com.example.hotelmanagement.dto.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RateOverrideResponse(
        Long id,
        String roomTypeCode,
        String roomTypeName,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal price,
        List<Integer> weekdays,
        Integer priority,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
