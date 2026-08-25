package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "bookings",
        indexes = {
                @Index(name = "idx_booking_public_id", columnList = "public_id"),
                @Index(name = "idx_booking_code", columnList = "booking_code"),
                @Index(name = "idx_booking_customer_status", columnList = "customer_id, status"),
                @Index(name = "idx_booking_status_created", columnList = "status, created_at DESC"),
                @Index(name = "idx_booking_source", columnList = "source_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "booking_code", nullable = false, unique = true, length = 20)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerProfile customerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private BookingSource source;

    @Column(name = "source_commission_percent_snapshot", precision = 5, scale = 2)
    private BigDecimal sourceCommissionPercentSnapshot;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;

    @Column(name = "contact_email", columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(nullable = false)
    @Builder.Default
    private Integer adults = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer children = 0;

    @Column(name = "rooms_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal roomsTotal = BigDecimal.ZERO;

    @Column(name = "services_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal servicesTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "deposit_percent_snapshot", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal depositPercentSnapshot = BigDecimal.ZERO;

    @Column(name = "required_deposit_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal requiredDepositAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "room_tax_percent_snapshot", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal roomTaxPercentSnapshot = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private BookingPaymentStatus paymentStatus = BookingPaymentStatus.UNPAID;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by")
    private StaffProfile checkedInBy;

    @Column(name = "checked_out_at")
    private OffsetDateTime checkedOutAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_out_by")
    private StaffProfile checkedOutBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<BookingRoom> bookingRooms = new HashSet<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<BookingGuest> bookingGuests = new HashSet<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<BookingStatusHistory> statusHistory = new HashSet<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<FolioCharge> folioCharges = new HashSet<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Payment> payments = new HashSet<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Invoice> invoices = new HashSet<>();
}
