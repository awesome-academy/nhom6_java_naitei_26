package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.payment.MockWalletResultRequest;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.MockWalletPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(value = "/api/admin/bookings", produces = "application/json")
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.payment.mock-wallet", name = "enabled", havingValue = "true")
public class StaffMockWalletPaymentController {

    private final MockWalletPaymentService mockWalletPaymentService;

    public StaffMockWalletPaymentController(MockWalletPaymentService mockWalletPaymentService) {
        this.mockWalletPaymentService = mockWalletPaymentService;
    }

    @PostMapping(
            value = "/{bookingPublicId}/payments/{paymentCode}/mock-wallet/result",
            consumes = "application/json"
    )
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_PAYMENT)
    public PaymentStatusResponse submitResult(
            @PathVariable String bookingPublicId,
            @PathVariable @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$") String paymentCode,
            @Valid @RequestBody MockWalletResultRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return mockWalletPaymentService.submitStaffResult(
                bookingPublicId,
                paymentCode,
                request,
                principal.getId()
        );
    }
}
