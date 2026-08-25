package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.payment.PaymentCreateRequest;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.PaymentService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Validated
@RequestMapping(value = "/api/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/{bookingPublicId}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody PaymentCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentResponse response = paymentService.createPayment(
                bookingPublicId,
                request,
                idempotencyKey,
                principal.getId()
        );
        return ResponseEntity.created(URI.create(
                "/api/bookings/" + bookingPublicId + "/payments/" + response.paymentCode()
        )).body(response);
    }

    @GetMapping("/{bookingPublicId}/payments/{paymentCode}")
    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public PaymentStatusResponse getPayment(
            @PathVariable String bookingPublicId,
            @PathVariable
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$")
            String paymentCode,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return paymentService.getPayment(bookingPublicId, paymentCode, principal.getId());
    }

    @PostMapping("/{bookingPublicId}/payments/{paymentCode}/cancel")
    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public PaymentStatusResponse cancelPayment(
            @PathVariable String bookingPublicId,
            @PathVariable
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$")
            String paymentCode,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return paymentService.cancelPayment(bookingPublicId, paymentCode, principal.getId());
    }
}
