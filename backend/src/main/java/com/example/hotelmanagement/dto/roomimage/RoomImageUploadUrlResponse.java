package com.example.hotelmanagement.dto.roomimage;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RoomImageUploadUrlResponse(
        UUID uploadId,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        OffsetDateTime expiresAt
) {
}
