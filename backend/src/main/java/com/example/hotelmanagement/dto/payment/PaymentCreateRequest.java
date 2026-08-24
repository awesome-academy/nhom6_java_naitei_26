package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod method
) {
}
