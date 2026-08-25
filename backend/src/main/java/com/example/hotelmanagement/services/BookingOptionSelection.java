package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;

import java.math.BigDecimal;

public record BookingOptionSelection(
        RoomType roomType,
        BookingPaymentOption paymentOption,
        CancellationPolicy cancellationPolicy,
        BigDecimal priceAdjustmentPercent
) {
}
