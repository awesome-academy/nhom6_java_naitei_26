package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.review.ReviewListResponse;
import com.example.hotelmanagement.entity.enums.ReviewStatus;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/reviews")
@Tag(name = "Admin - Reviews", description = "Review moderation for staff and administrators")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.REVIEW_MODERATE)
    @Operation(summary = "List reviews for moderation")
    public ReviewListResponse listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) @Size(max = 30) String roomTypeCode,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return reviewService.listReviews(status, roomTypeCode, rating, page, size);
    }
}
