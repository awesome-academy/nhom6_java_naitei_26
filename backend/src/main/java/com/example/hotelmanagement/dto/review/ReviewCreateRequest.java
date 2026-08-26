package com.example.hotelmanagement.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @NotNull @Min(1) @Max(5) Integer overallRating,
        @Min(1) @Max(5) Integer roomRating,
        @Min(1) @Max(5) Integer cleanlinessRating,
        @Min(1) @Max(5) Integer serviceRating,
        @Min(1) @Max(5) Integer valueRating,
        @Size(max = 200) String title,
        @Size(max = 10000) String comment
) {
}
