package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RoomTypeResponse(
        String code,
        Long roomTypeId,
        String name,
        String slug,
        String description,
        Integer bedCount,
        Integer maxOccupancy,
        Integer maxAdults,
        Integer maxChildren,
        BigDecimal basePrice,
        String currency,
        BigDecimal extraBedPrice,
        BigDecimal sizeSqm,
        Boolean isActive,
        Integer sortOrder,
        Boolean payAtHotelEnabled,
        BigDecimal payAtHotelPriceAdjustmentPercent,
        List<RoomTypeCancellationPolicyOptionResponse> onlineCancellationPolicyOptions,
        List<RoomTypeBookingOptionResponse> bookingOptions,
        List<RoomTypeBedResponse> beds,
        List<AmenityResponse> amenities,
        List<RoomImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public CancellationPolicyResponse cancellationPolicy() {
        if (onlineCancellationPolicyOptions == null || onlineCancellationPolicyOptions.isEmpty()) {
            return null;
        }
        return onlineCancellationPolicyOptions.getFirst().cancellationPolicy();
    }
}
