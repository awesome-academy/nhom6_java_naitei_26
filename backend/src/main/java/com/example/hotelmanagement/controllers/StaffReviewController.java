package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.review.StaffReviewListResponse;
import com.example.hotelmanagement.entity.enums.ReviewStatus;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(value = "/api/staff/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Staff reviews", description = "Read reviews and reply without moderation access")
public class StaffReviewController {

    private final ReviewService reviewService;

    public StaffReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.REVIEW_REPLY)
    @Operation(summary = "List reviews available for staff replies")
    public StaffReviewListResponse listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String roomTypeCode,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return reviewService.listReviewsForStaffReply(status, roomTypeCode, rating, page, size);
    }
}
