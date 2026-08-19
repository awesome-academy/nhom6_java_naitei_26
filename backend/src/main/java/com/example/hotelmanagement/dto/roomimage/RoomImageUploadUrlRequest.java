package com.example.hotelmanagement.dto.roomimage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomImageUploadUrlRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long fileSize
) {
}
