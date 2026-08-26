package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.review.ReviewCreateRequest;
import com.example.hotelmanagement.dto.review.ReviewModerationRequest;
import com.example.hotelmanagement.dto.review.ReviewReplyRequest;
import com.example.hotelmanagement.dto.review.ReviewResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/bookings/{bookingPublicId}/review", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @PreAuthorize(PermissionExpressions.REVIEW_CREATE)
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReviewResponse response = reviewService.createReview(bookingPublicId, request, principal.getId());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping(value = "/moderate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.REVIEW_MODERATE)
    public ResponseEntity<ReviewResponse> moderate(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody ReviewModerationRequest request
    ) {
        return ResponseEntity.ok(reviewService.moderate(bookingPublicId, request));
    }

    @PostMapping(value = "/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.REVIEW_REPLY)
    public ResponseEntity<ReviewResponse> reply(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody ReviewReplyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(reviewService.reply(bookingPublicId, request, principal.getId()));
    }
}
