package com.example.hotelmanagement.dto.roomimage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RoomImageOrderRequest(
        @NotEmpty List<@Valid @NotNull UUID> imageIds
) {
}
