package com.example.hotelmanagement.dto.invoice;

import java.time.OffsetDateTime;

public record InvoicePdfResponse(
        String url,
        OffsetDateTime expiresAt
) {
}
