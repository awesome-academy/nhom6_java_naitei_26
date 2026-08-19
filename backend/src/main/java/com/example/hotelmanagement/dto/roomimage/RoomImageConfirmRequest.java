package com.example.hotelmanagement.dto.roomimage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoomImageConfirmRequest(
        @NotNull UUID uploadId,
        @NotBlank @Size(max = 200) String altText
) {
}
