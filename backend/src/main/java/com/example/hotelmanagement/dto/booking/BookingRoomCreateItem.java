package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "BookingRoomCreateItem", description = "A single room stay requested as part of a new booking")
public record BookingRoomCreateItem(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String roomTypeCode,
        @NotNull com.example.hotelmanagement.entity.enums.BookingPaymentOption paymentOption,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String cancellationPolicyCode,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @Min(1) Integer adults,
        @Min(0) Integer children,
        @Size(max = 150) String guestFullName
) {
    public BookingRoomCreateItem(
            Long roomId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer children
    ) {
        this(String.valueOf(roomId), com.example.hotelmanagement.entity.enums.BookingPaymentOption.ONLINE,
                "FLEXIBLE", checkInDate, checkOutDate, adults, children, "Guest");
    }

    public Long roomId() {
        try {
            return Long.valueOf(roomTypeCode);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
