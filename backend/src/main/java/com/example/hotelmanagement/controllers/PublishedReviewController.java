package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.review.PublishedReviewListResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(value = "/api/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reviews", description = "Customer-facing published guest reviews")
public class PublishedReviewController {

    private final ReviewService reviewService;

    public PublishedReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/published")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    @Operation(
            summary = "List published guest reviews",
            description = "Returns approved reviews and aggregates across all PUBLISHED reviews. "
                    + "Customer-facing responses omit contact, booking, moderation, and internal actor data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Published reviews returned"),
            @ApiResponse(responseCode = "403", description = "The caller lacks room:read")
    })
    public PublishedReviewListResponse listPublishedReviews(
            @RequestParam(defaultValue = "0") @Min(0) @Schema(description = "Zero-based page number") Integer page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50)
            @Schema(description = "Number of reviews per page") Integer size
    ) {
        return reviewService.listPublishedReviews(page, size);
    }
}
