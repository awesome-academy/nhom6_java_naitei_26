package com.example.hotelmanagement.dto.roomtype;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoomTypeBedsRequest(
        @NotEmpty @Size(max = 6) List<@Valid RoomTypeBedRequest> beds
) {
}
