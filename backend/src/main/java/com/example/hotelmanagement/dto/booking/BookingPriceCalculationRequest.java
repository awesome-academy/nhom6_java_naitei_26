package com.example.hotelmanagement.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record BookingPriceCalculationRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String roomTypeCode,
        @NotNull com.example.hotelmanagement.entity.enums.BookingPaymentOption paymentOption,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String cancellationPolicyCode,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer adults,
        @NotNull @Min(0) Integer children
) {
    public BookingPriceCalculationRequest(
            Long roomId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer children
    ) {
        this(String.valueOf(roomId), com.example.hotelmanagement.entity.enums.BookingPaymentOption.ONLINE,
                "FLEXIBLE", checkInDate, checkOutDate, adults, children);
    }

    public Long roomId() {
        try {
            return Long.valueOf(roomTypeCode);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
