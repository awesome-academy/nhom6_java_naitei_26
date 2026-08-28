package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record StaffBookingRoomCreateItem(
        @NotBlank @Size(max = 20) String roomNumber,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]+$") String roomTypeCode,
        @NotNull BookingPaymentOption paymentOption,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer guestCount,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid StaffBookingGuestCreateItem> guests
) {
}
