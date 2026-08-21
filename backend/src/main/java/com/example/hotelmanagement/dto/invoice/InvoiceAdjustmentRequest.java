package com.example.hotelmanagement.dto.invoice;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InvoiceAdjustmentRequest(
        @NotBlank
        @Size(max = 200)
        String description,
        @NotNull
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount
) {
}
