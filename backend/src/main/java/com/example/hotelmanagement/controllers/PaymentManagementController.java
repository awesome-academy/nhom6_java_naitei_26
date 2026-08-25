package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.payment.PaymentCashVerificationRequest;
import com.example.hotelmanagement.dto.payment.PaymentDetailResponse;
import com.example.hotelmanagement.dto.payment.PaymentListResponse;
import com.example.hotelmanagement.dto.payment.PaymentRefundRequest;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.PaymentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;

@RestController
@Validated
@RequestMapping(value = "/api/admin/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin - Payments", description = "Payment management for staff and administrators")
public class PaymentManagementController {

    private final PaymentManagementService paymentManagementService;

    public PaymentManagementController(PaymentManagementService paymentManagementService) {
        this.paymentManagementService = paymentManagementService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    @Operation(summary = "List payments with filters")
    public PaymentListResponse listPayments(
            @RequestParam(required = false) @Size(max = 120) String booking,
            @RequestParam(required = false) Set<PaymentStatus> status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return paymentManagementService.listPayments(booking, status, method, from, to, page, size);
    }

    @GetMapping("/{paymentCode}")
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    @Operation(summary = "Get payment detail")
    public PaymentDetailResponse getPayment(
            @PathVariable
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$")
            String paymentCode
    ) {
        return paymentManagementService.getPayment(paymentCode);
    }

    @PostMapping(value = "/{paymentCode}/verify-cash", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    @Operation(summary = "Verify a CASH payment manually")
    public PaymentDetailResponse verifyCashPayment(
            @PathVariable
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$")
            String paymentCode,
            @Valid @RequestBody(required = false) PaymentCashVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return paymentManagementService.verifyCashPayment(paymentCode, request, principal.getId());
    }

    @PostMapping(value = "/{paymentCode}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.PAYMENT_MANAGE)
    @Operation(summary = "Request a manual refund")
    public ResponseEntity<PaymentDetailResponse> requestRefund(
            @PathVariable
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$")
            String paymentCode,
            @Valid @RequestBody PaymentRefundRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentDetailResponse response = paymentManagementService.requestRefund(
                paymentCode,
                request,
                principal.getId()
        );
        return ResponseEntity.created(URI.create("/api/admin/payments/" + paymentCode)).body(response);
    }
}
