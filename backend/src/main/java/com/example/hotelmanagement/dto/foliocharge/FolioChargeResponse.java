package com.example.hotelmanagement.dto.foliocharge;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FolioChargeResponse(
        Long id,
        String bookingPublicId,
        String serviceItemCode,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineSubtotal,
        BigDecimal discountAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        OffsetDateTime chargedAt,
        Long chargedBy,
        Boolean isVoided,
        OffsetDateTime voidedAt,
        Long voidedBy,
        String voidReason
) {
}
