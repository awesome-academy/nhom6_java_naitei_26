package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.refund.RefundCompleteRequest;
import com.example.hotelmanagement.dto.refund.RefundResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundReason;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RefundRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Computes and processes refunds owed when a booking is cancelled (BE-7.4), reading the
 * immutable {@code cancellation_policy_snapshot} captured per booking_room at booking time
 * (DATABASE_DESIGN 5.3/9.6) rather than the live CancellationPolicy — the whole point of the
 * snapshot is that later policy edits must never change what an already-cancelled guest is owed.
 *
 * <p>Workflow note: the task that requested this service described 4 steps
 * (PENDING → APPROVED (Admin) → PROCESSING → COMPLETED), but the seeded {@link RefundStatus}
 * enum and the authoritative PROJECT_PLAN.md ticket (BE-7.4) both only define 3
 * (PENDING → PROCESSING → COMPLETED), with no {@code APPROVED} value in the DB enum. Adding a
 * 4th enum value would require altering a live ENUM column and its CHECK constraints, which is
 * out of scope here. This service treats the admin "approve" action as the existing
 * PENDING → PROCESSING transition (gated by refund:approve, i.e. an admin decision), and
 * PROCESSING → COMPLETED as the step where money actually moves and the payment ledger is
 * synchronized. This mismatch should be confirmed with whoever wrote the task.</p>
 */
@Service
@Transactional
public class RefundService {

    private static final Set<RefundStatus> ACTIVE_REFUND_STATUSES = Set.of(
            RefundStatus.PENDING, RefundStatus.PROCESSING
    );
    private static final Set<PaymentStatus> RECEIVED_PAYMENT_STATUSES = Set.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED
    );
    private static final String CANCEL_ANY_AUTHORITY_FALLBACK = "refund:approve";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime FALLBACK_CHECK_IN_TIME = LocalTime.of(14, 0);
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RefundRepository refundRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RefundService(
            RefundRepository refundRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            PaymentLedgerService paymentLedgerService,
            HotelSettingsRepository hotelSettingsRepository,
            EmailService emailService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.refundRepository = refundRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.paymentLedgerService = paymentLedgerService;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * BR-005: only the booking's contact customer, or an actor holding refund:approve
     * (staff/admin), may request a refund for it.
     */
    @PreAuthorize(PermissionExpressions.REFUND_REQUEST)
    public RefundResponse requestRefund(String bookingPublicId, Long actorUserId) {
        Booking booking = getExistingBooking(bookingPublicId);
        boolean isOwner = isOwner(booking, actorUserId);
        ensureCanRequestRefund(isOwner);

        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new BusinessValidationException("Refunds can only be requested for a cancelled booking");
        }
        if (booking.getCancelledAt() == null) {
            throw new BusinessValidationException("Cancelled booking is missing its cancellation timestamp");
        }
        if (refundRepository.existsByBooking_IdAndStatusIn(booking.getId(), ACTIVE_REFUND_STATUSES)) {
            throw new DuplicateResourceException("Refund", "booking id", booking.getId().toString());
        }

        Payment payment = paymentRepository
                .findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(booking.getId(), RECEIVED_PAYMENT_STATUSES)
                .orElseThrow(() -> new BusinessValidationException(
                        "No received payment was found to refund for this booking"
                ));

        RefundCalculation calculation = calculateRefund(booking);
        if (calculation.netRefund().signum() <= 0) {
            throw new BusinessValidationException("No refund is due based on the cancellation policy");
        }

        Refund refund = Refund.builder()
                .payment(payment)
                .booking(booking)
                .amount(calculation.netRefund())
                .reason(isOwner ? RefundReason.CUSTOMER_CANCEL : RefundReason.HOTEL_CANCEL)
                .status(RefundStatus.PENDING)
                .policyApplied(calculation.policyAppliedJson())
                .requestedBy(actorUserId)
                .build();

        return mapResponse(refundRepository.save(refund));
    }

    /** PENDING -> PROCESSING. See the class-level note about the requested APPROVED status. */
    @PreAuthorize(PermissionExpressions.REFUND_APPROVE)
    public RefundResponse approve(String bookingPublicId, Long refundId, Long actorUserId) {
        Refund refund = getRefundForUpdate(bookingPublicId, refundId);
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessValidationException("Only a PENDING refund can be approved");
        }
        refund.setApprovedBy(actorUserId);
        refund.setStatus(RefundStatus.PROCESSING);
        return mapResponse(refundRepository.save(refund));
    }

    /** PROCESSING -> COMPLETED. Synchronizes the payment/booking/invoice ledger in-transaction. */
    @PreAuthorize(PermissionExpressions.REFUND_APPROVE)
    public RefundResponse complete(String bookingPublicId, Long refundId, RefundCompleteRequest request) {
        Refund refund = getRefundForUpdate(bookingPublicId, refundId);
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            throw new BusinessValidationException("Only a PROCESSING refund can be completed");
        }
        if (request != null && request.providerRefundId() != null && !request.providerRefundId().isBlank()) {
            refund.setProviderRefundId(request.providerRefundId().strip());
        }
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setProcessedAt(OffsetDateTime.now(clock));

        Refund saved = refundRepository.saveAndFlush(refund);
        paymentLedgerService.synchronizeCompletedRefund(saved);
        emailService.sendPaymentRefundEmail(saved);
        return mapResponse(saved);
    }

    private void ensureCanRequestRefund(boolean isOwner) {
        if (isOwner) {
            return;
        }
        boolean hasApproveAuthority = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> CANCEL_ANY_AUTHORITY_FALLBACK.equals(authority.getAuthority()));
        if (!hasApproveAuthority) {
            throw new AccessDeniedException(
                    "Only the booking's contact customer or a user with " + CANCEL_ANY_AUTHORITY_FALLBACK
                            + " can request a refund for this booking"
            );
        }
    }

    private boolean isOwner(Booking booking, Long actorUserId) {
        return booking.getCustomerProfile() != null
                && booking.getCustomerProfile().getUser().getId().equals(actorUserId);
    }

    private RefundCalculation calculateRefund(Booking booking) {
        List<BookingRoom> bookingRooms = booking.getBookingRooms().stream()
                .sorted(Comparator.comparing(BookingRoom::getCheckInDate))
                .toList();
        if (bookingRooms.isEmpty()) {
            throw new BusinessValidationException("Booking has no rooms to calculate a refund for");
        }

        ZoneId hotelZone = resolveHotelZone();
        LocalTime standardCheckInTime = resolveStandardCheckInTime();

        BigDecimal roomsGrossRefund = BigDecimal.ZERO;
        List<RoomRefundBreakdown> breakdowns = new java.util.ArrayList<>();
        RoomMatch earliestMatch = null;
        for (BookingRoom bookingRoom : bookingRooms) {
            RoomMatch match = matchRule(bookingRoom, booking.getCancelledAt(), hotelZone, standardCheckInTime);
            if (earliestMatch == null) {
                earliestMatch = match;
            }
            BigDecimal roomGrossRefund = normalize(
                    bookingRoom.getRoomSubtotal().multiply(match.refundPercent()).divide(HUNDRED)
            );
            roomsGrossRefund = roomsGrossRefund.add(roomGrossRefund);
            breakdowns.add(new RoomRefundBreakdown(
                    bookingRoom.getId(),
                    bookingRoom.getCheckInDate().toString(),
                    match.hoursBeforeCancel(),
                    match.matchedMinHoursBefore(),
                    match.refundPercent(),
                    normalize(bookingRoom.getRoomSubtotal()),
                    roomGrossRefund
            ));
        }

        BigDecimal servicesTotal = normalize(booking.getServicesTotal());
        BigDecimal servicesRefundPercent = earliestMatch.refundPercent();
        BigDecimal servicesGrossRefund = normalize(servicesTotal.multiply(servicesRefundPercent).divide(HUNDRED));

        BigDecimal grossRefund = normalize(roomsGrossRefund.add(servicesGrossRefund));
        BigDecimal commissionPercent = booking.getSourceCommissionPercentSnapshot() == null
                ? BigDecimal.ZERO
                : booking.getSourceCommissionPercentSnapshot();
        BigDecimal commissionAmount = normalize(grossRefund.multiply(commissionPercent).divide(HUNDRED));
        BigDecimal netRefund = normalize(grossRefund.subtract(commissionAmount));

        RefundCalculationSnapshot snapshot = new RefundCalculationSnapshot(
                breakdowns, servicesTotal, servicesRefundPercent, servicesGrossRefund,
                grossRefund, commissionPercent, netRefund
        );
        return new RefundCalculation(netRefund, writePolicyAppliedJson(snapshot));
    }

    private String writePolicyAppliedJson(RefundCalculationSnapshot snapshot) {
        try {
            return objectMapper.writer()
                    .with(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessValidationException("Unable to serialize the refund calculation snapshot");
        }
    }

    private RoomMatch matchRule(
            BookingRoom bookingRoom,
            OffsetDateTime cancelledAt,
            ZoneId hotelZone,
            LocalTime standardCheckInTime
    ) {
        String snapshotJson = bookingRoom.getCancellationPolicySnapshot();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new BusinessValidationException(
                    "Booking room " + bookingRoom.getId() + " has no cancellation policy snapshot"
            );
        }
        CancellationPolicySnapshotForRefund snapshot;
        try {
            snapshot = objectMapper.readValue(snapshotJson, CancellationPolicySnapshotForRefund.class);
        } catch (Exception exception) {
            throw new BusinessValidationException(
                    "Booking room " + bookingRoom.getId() + " has an unreadable cancellation policy snapshot"
            );
        }
        if (snapshot.rules() == null || snapshot.rules().isEmpty()) {
            throw new BusinessValidationException(
                    "Booking room " + bookingRoom.getId() + " has no cancellation policy rules"
            );
        }

        OffsetDateTime scheduledCheckIn = bookingRoom.getCheckInDate()
                .atTime(standardCheckInTime)
                .atZone(hotelZone)
                .toOffsetDateTime();
        long hoursBeforeCancel = Math.max(0, Duration.between(cancelledAt, scheduledCheckIn).toHours());

        RuleSnapshotForRefund matchedRule = snapshot.rules().stream()
                .filter(rule -> rule.minHoursBefore() != null && rule.minHoursBefore() <= hoursBeforeCancel)
                .max(Comparator.comparing(RuleSnapshotForRefund::minHoursBefore))
                .orElseThrow(() -> new BusinessValidationException(
                        "Booking room " + bookingRoom.getId()
                                + " has no cancellation policy rule matching " + hoursBeforeCancel + " hours before check-in"
                ));

        return new RoomMatch(hoursBeforeCancel, matchedRule.minHoursBefore(), matchedRule.refundPercent());
    }

    private ZoneId resolveHotelZone() {
        String configured = hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY);
        if (configured == null || configured.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            return FALLBACK_ZONE;
        }
    }

    private LocalTime resolveStandardCheckInTime() {
        String configured = hotelSettingsRepository.getStringValue(HotelSettingsService.CHECK_IN_TIME_KEY);
        if (configured == null || configured.isBlank()) {
            return FALLBACK_CHECK_IN_TIME;
        }
        try {
            return LocalTime.parse(configured, TIME_FORMAT);
        } catch (Exception exception) {
            return FALLBACK_CHECK_IN_TIME;
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private Booking getExistingBooking(String bookingPublicId) {
        if (bookingPublicId == null || bookingPublicId.isBlank()) {
            throw new BusinessValidationException("Booking public id cannot be blank");
        }
        return bookingRepository.findByPublicId(bookingPublicId.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));
    }

    private Refund getRefundForUpdate(String bookingPublicId, Long refundId) {
        if (refundId == null || refundId <= 0) {
            throw new BusinessValidationException("Refund id must be a positive number");
        }
        Refund refund = refundRepository.findForUpdateById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId.toString()));
        if (refund.getBooking() == null || !refund.getBooking().getPublicId().equals(bookingPublicId)) {
            throw new ResourceNotFoundException("Refund", refundId.toString());
        }
        return refund;
    }

    private RefundResponse mapResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getBooking().getPublicId(),
                refund.getPayment().getPaymentCode(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getPolicyApplied(),
                refund.getRequestedBy(),
                refund.getApprovedBy(),
                refund.getProviderRefundId(),
                refund.getProcessedAt(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }

    private record RoomMatch(long hoursBeforeCancel, int matchedMinHoursBefore, BigDecimal refundPercent) {
    }

    private record RefundCalculation(BigDecimal netRefund, String policyAppliedJson) {
    }

    private record RoomRefundBreakdown(
            @JsonProperty("booking_room_id") Long bookingRoomId,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("hours_before_cancel") long hoursBeforeCancel,
            @JsonProperty("matched_min_hours_before") int matchedMinHoursBefore,
            @JsonProperty("refund_percent") BigDecimal refundPercent,
            @JsonProperty("room_subtotal") BigDecimal roomSubtotal,
            @JsonProperty("room_gross_refund") BigDecimal roomGrossRefund
    ) {
    }

    private record RefundCalculationSnapshot(
            @JsonProperty("rooms") List<RoomRefundBreakdown> rooms,
            @JsonProperty("services_total") BigDecimal servicesTotal,
            @JsonProperty("services_refund_percent") BigDecimal servicesRefundPercent,
            @JsonProperty("services_gross_refund") BigDecimal servicesGrossRefund,
            @JsonProperty("gross_refund") BigDecimal grossRefund,
            @JsonProperty("source_commission_percent_snapshot") BigDecimal sourceCommissionPercentSnapshot,
            @JsonProperty("net_refund") BigDecimal netRefund
    ) {
    }

    private record CancellationPolicySnapshotForRefund(
            String code,
            String name,
            @JsonProperty("rules") List<RuleSnapshotForRefund> rules
    ) {
    }

    private record RuleSnapshotForRefund(
            @JsonProperty("min_hours_before") Integer minHoursBefore,
            @JsonProperty("refund_percent") BigDecimal refundPercent
    ) {
    }
}
