package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "BookingCheckInRequest", description = "Guest information captured during check-in")
public record BookingCheckInRequest(
        @Size(max = 50) List<@NotNull @Valid BookingCheckInRoomItem> rooms
) {
}
