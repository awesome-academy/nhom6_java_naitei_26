package com.example.hotelmanagement.dto.refund;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "RefundCompleteRequest", description = "Payload to mark a PROCESSING refund as COMPLETED")
public record RefundCompleteRequest(
        @Schema(description = "External payment gateway refund reference, if any", nullable = true)
        @Size(max = 120)
        String providerRefundId
) {
}
