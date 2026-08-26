package com.example.hotelmanagement.dto.review;

import com.example.hotelmanagement.entity.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewModerationRequest(
        @NotNull ReviewStatus status,
        @Size(max = 1000) String moderationReason
) {
}
