package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full booking detail response for Staff view, including all related data.
 */
public record BookingStaffDetailResponse(
        // Booking info
        String publicId,
        String bookingCode,
        BookingStatus status,
        BookingPaymentStatus paymentStatus,
        String sourceCode,
        String sourceName,
        String sourceCommissionPercentSnapshot,
        String contactName,
        String contactEmail,
        String contactPhone,
        String contactAddress,
        Integer adults,
        Integer children,
        BigDecimal roomsTotal,
        BigDecimal servicesTotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        String currency,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        String specialRequests,
        String internalNotes,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime confirmedAt,
        String confirmedByName,
        OffsetDateTime checkedInAt,
        String checkedInByName,
        OffsetDateTime checkedOutAt,
        String checkedOutByName,
        OffsetDateTime cancelledAt,
        Long cancelledBy,
        String cancellationReason,
        OffsetDateTime createdAt,

        // Customer info (if available)
        Long customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        Integer customerLoyaltyPoints,

        // Related data
        List<BookingRoomDetailResponse> rooms,
        List<BookingGuestResponse> guests,
        List<FolioChargeResponse> folioCharges,
        List<PaymentResponse> payments,
        List<InvoiceResponse> invoices,
        List<BookingStatusHistoryResponse> statusHistory
) {

    /**
     * Detailed booking room info including nightly prices.
     */
    public record BookingRoomDetailResponse(
            Long id,
            Long roomId,
            String roomNumber,
            String roomTypeCode,
            String roomTypeName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int nights,
            BigDecimal roomSubtotal,
            String bookingRoomStatus,
            Integer guestCount,
            String cancellationPolicyCode,
            String cancellationPolicyName,
            String cancellationPolicySnapshot,
            String paymentOption,
            BigDecimal priceAdjustmentPercentSnapshot,
            OffsetDateTime assignedAt,
            String assignedByName,
            List<BookingRoomNightResponse> nightlyRates
    ) {}

    /**
     * Guest info for staff view.
     */
    public record BookingGuestResponse(
            Long id,
            Long bookingRoomId,
            String roomNumber,
            String fullName,
            String nationality,
            String idDocumentType,
            boolean hasIdDocument,
            String dateOfBirth,
            OffsetDateTime createdAt
    ) {}

    /**
     * Nightly rate info.
     */
    public record BookingRoomNightResponse(
            LocalDate stayDate,
            BigDecimal price
    ) {}
}
