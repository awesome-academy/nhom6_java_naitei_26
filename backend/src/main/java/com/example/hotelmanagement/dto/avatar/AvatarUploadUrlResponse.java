package com.example.hotelmanagement.dto.avatar;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AvatarUploadUrlResponse(
        UUID uploadId,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        OffsetDateTime expiresAt
) {
}
