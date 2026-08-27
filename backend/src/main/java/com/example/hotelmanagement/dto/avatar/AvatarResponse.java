package com.example.hotelmanagement.dto.avatar;

import java.time.OffsetDateTime;

public record AvatarResponse(
        String userPublicId,
        String avatarUrl,
        OffsetDateTime avatarUrlExpiresAt
) {
}
