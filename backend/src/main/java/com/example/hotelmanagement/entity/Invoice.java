package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.InvoicePaymentStatus;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "invoices",
        indexes = {
                @Index(name = "idx_inv_booking", columnList = "booking_id"),
                @Index(name = "idx_inv_status_issued", columnList = "status, issued_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true)
    private String publicId;

    @Column(name = "invoice_number", unique = true, length = 30)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private InvoicePaymentStatus paymentStatus = InvoicePaymentStatus.UNPAID;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_by_user_id")
    private Long issuedByUserId;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "buyer_address", columnDefinition = "TEXT")
    private String buyerAddress;

    @Column(name = "buyer_tax_code", length = 20)
    private String buyerTaxCode;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "pdf_storage_key", columnDefinition = "TEXT")
    private String pdfStorageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_invoice_id")
    private Invoice replacesInvoice;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_by_user_id")
    private Long voidedByUserId;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<InvoiceItem> items = new HashSet<>();
}
