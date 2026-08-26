package com.example.hotelmanagement.dto.payment;

import jakarta.validation.constraints.Size;

public record PaymentCashVerificationRequest(
        @Size(max = 120, message = "Provider transaction id must be at most 120 characters")
        String providerTxnId
) {
}
