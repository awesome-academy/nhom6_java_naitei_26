package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record StaffBookingPriceCalculationRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String roomTypeCode,
        @NotNull BookingPaymentOption paymentOption,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer adults
) {
}
