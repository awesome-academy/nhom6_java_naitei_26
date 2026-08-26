package com.example.hotelmanagement.dto.invoice;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvoiceBuyerUpdateRequest(
        @NotBlank
        @Size(max = 150)
        String buyerName,

        @Size(max = 2000)
        String buyerAddress,

        @Size(max = 20)
        String buyerTaxCode,

        @Email
        @Size(max = 255)
        String buyerEmail
) {
}
