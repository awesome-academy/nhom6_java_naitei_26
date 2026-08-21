package com.example.hotelmanagement.dto.invoice;

import com.example.hotelmanagement.entity.enums.InvoiceLineType;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        InvoiceLineType lineType,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineSubtotal,
        BigDecimal discountAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        String referenceType,
        Long referenceId,
        Integer sortOrder
) {
}
