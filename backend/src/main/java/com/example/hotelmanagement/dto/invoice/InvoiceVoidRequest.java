package com.example.hotelmanagement.dto.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "InvoiceVoidRequest", description = "Payload to void an issued invoice")
public record InvoiceVoidRequest(
        @NotBlank
        @Size(max = 2000)
        String reason,

        @Schema(description = "Whether to immediately create a replacement DRAFT invoice cloning this invoice's lines")
        boolean createReplacement
) {
}
