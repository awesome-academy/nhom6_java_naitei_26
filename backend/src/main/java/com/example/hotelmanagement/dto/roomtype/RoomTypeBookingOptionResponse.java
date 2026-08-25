package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;

import java.math.BigDecimal;

public record RoomTypeBookingOptionResponse(
        String optionKey,
        BookingPaymentOption paymentOption,
        CancellationPolicyResponse cancellationPolicy,
        BigDecimal priceAdjustmentPercent
) {
}
