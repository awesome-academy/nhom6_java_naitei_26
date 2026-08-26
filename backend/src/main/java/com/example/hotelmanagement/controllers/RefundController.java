package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.refund.RefundCompleteRequest;
import com.example.hotelmanagement.dto.refund.RefundPreviewResponse;
import com.example.hotelmanagement.dto.refund.RefundResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/bookings/{bookingPublicId}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.REFUND_REQUEST)
    public ResponseEntity<RefundResponse> getLatestRefund(
            @PathVariable String bookingPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return refundService.getLatestRefund(bookingPublicId, principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/preview")
    @PreAuthorize(PermissionExpressions.REFUND_REQUEST)
    public ResponseEntity<RefundPreviewResponse> previewRefund(
            @PathVariable String bookingPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(refundService.previewRefund(bookingPublicId, principal.getId()));
    }

    @PostMapping
    @PreAuthorize(PermissionExpressions.REFUND_REQUEST)
    public ResponseEntity<RefundResponse> requestRefund(
            @PathVariable String bookingPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RefundResponse response = refundService.requestRefund(bookingPublicId, principal.getId());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/{refundId}/approve")
    @PreAuthorize(PermissionExpressions.REFUND_APPROVE)
    public ResponseEntity<RefundResponse> approve(
            @PathVariable String bookingPublicId,
            @PathVariable Long refundId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(refundService.approve(bookingPublicId, refundId, principal.getId()));
    }

    @PostMapping(value = "/{refundId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.REFUND_APPROVE)
    public ResponseEntity<RefundResponse> complete(
            @PathVariable String bookingPublicId,
            @PathVariable Long refundId,
            @Valid @RequestBody(required = false) RefundCompleteRequest request
    ) {
        return ResponseEntity.ok(refundService.complete(bookingPublicId, refundId, request));
    }
}
