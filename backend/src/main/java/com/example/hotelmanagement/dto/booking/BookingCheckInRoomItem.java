package com.example.hotelmanagement.dto.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookingCheckInRoomItem(
        @NotNull Long bookingRoomId,
        @NotNull @Min(1) Integer guestCount,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid StaffBookingGuestCreateItem> guests
) {
}
