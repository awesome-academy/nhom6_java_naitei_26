package com.example.hotelmanagement.dto.roomimage;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomImageResponse(
        UUID imageId,
        String downloadUrl,
        OffsetDateTime downloadUrlExpiresAt,
        String altText,
        Boolean isPrimary,
        Integer sortOrder
) {
}
