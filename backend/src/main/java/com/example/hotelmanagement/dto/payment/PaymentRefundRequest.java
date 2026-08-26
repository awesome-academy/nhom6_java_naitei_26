package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.RefundReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRefundRequest(
        @NotNull(message = "Refund amount is required")
        @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
        BigDecimal amount,
        @NotNull(message = "Refund reason is required")
        RefundReason reason
) {
}
