package com.example.hotelmanagement.dto.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "InvoiceVoidResponse", description = "Result of voiding an invoice, with the replacement draft when one was created")
public record InvoiceVoidResponse(
        InvoiceResponse voidedInvoice,

        @Schema(nullable = true)
        InvoiceResponse replacementInvoice
) {
}
