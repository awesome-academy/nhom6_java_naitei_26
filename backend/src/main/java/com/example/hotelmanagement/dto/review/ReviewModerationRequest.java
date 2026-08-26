package com.example.hotelmanagement.dto.review;

import com.example.hotelmanagement.entity.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewModerationRequest(
        @NotNull ReviewStatus status
) {
}
