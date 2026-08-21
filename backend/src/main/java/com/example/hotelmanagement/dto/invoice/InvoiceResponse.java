package com.example.hotelmanagement.dto.invoice;

import com.example.hotelmanagement.entity.enums.InvoicePaymentStatus;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record InvoiceResponse(
        String publicId,
        String invoiceNumber,
        String bookingPublicId,
        InvoiceStatus status,
        InvoicePaymentStatus paymentStatus,
        OffsetDateTime issuedAt,
        Long issuedBy,
        String buyerName,
        String buyerAddress,
        String buyerTaxCode,
        String buyerEmail,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        String currency,
        List<InvoiceItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
