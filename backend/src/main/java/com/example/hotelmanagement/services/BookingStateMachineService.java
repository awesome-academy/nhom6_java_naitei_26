package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingBedSummaryResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomNightResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomResponse;
import com.example.hotelmanagement.audit.AuditMutation;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.BookingStatusHistory;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BedType;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the booking status transitions from DATABASE_DESIGN 8.1 (BR-010, BR-011) and BR-005
 * ownership on top of the DB-level trg_booking_state_machine / trg_booking_cancel_check triggers,
 * which intentionally do not know "who" the acting user is. Syncing booking_rooms.status (8.2) is
 * left entirely to trg_booking_sync_rooms; this service never writes BookingRoom directly.
 */
@Service
@Transactional
public class BookingStateMachineService {

    private static final String CANCEL_ANY_AUTHORITY = "booking:cancel_any";

    private final BookingRepository bookingRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final Clock clock;

    @Autowired
    public BookingStateMachineService(
            BookingRepository bookingRepository,
            StaffProfileRepository staffProfileRepository,
            PaymentRepository paymentRepository,
            InvoiceService invoiceService,
            EmailService emailService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.clock = clock;
    }

    public BookingStateMachineService(
            BookingRepository bookingRepository,
            StaffProfileRepository staffProfileRepository,
            InvoiceService invoiceService,
            EmailService emailService,
            Clock clock
    ) {
        this(bookingRepository, staffProfileRepository, null, invoiceService, emailService, clock);
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    @AuditMutation(action = "BOOKING_CHECKED_IN", entityType = "booking", actorUserIdArgumentIndex = 1)
    public BookingResponse checkIn(String bookingPublicId, Long staffUserId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElse(null);
        ensureAuthorizedStaffActor(staffUserId, staff, "check in", "booking:check_in");
        ensureAllRoomsAssigned(booking);

        applyTransition(booking, BookingStatus.CHECKED_IN, ActorType.USER, staffUserId, StatusChangeSource.MANUAL, null);
        booking.setCheckedInBy(staff);

        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * Confirms a staff-created walk-in booking as the front-desk actor. This is separate from
     * {@link #confirm(String)} because payment callbacks are system transitions, while a staff
     * confirmation must be attributed to the authenticated employee.
     */
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    @AuditMutation(action = "BOOKING_CONFIRMED", entityType = "booking", actorUserIdArgumentIndex = 1)
    public BookingResponse confirmAsStaff(String bookingPublicId, Long staffUserId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId).orElse(null);
        ensureAuthorizedStaffActor(staffUserId, staff, "confirm a booking", "booking:check_in");
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessValidationException("Only pending bookings can be confirmed");
        }
        ensureAllRoomsAssigned(booking);

        applyTransition(
                booking,
                BookingStatus.CONFIRMED,
                ActorType.USER,
                staffUserId,
                StatusChangeSource.MANUAL,
                null
        );
        Booking confirmedBooking = bookingRepository.saveAndFlush(booking);
        emailService.sendBookingConfirmedEmail(confirmedBooking);
        return mapResponse(confirmedBooking);
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_OUT)
    @AuditMutation(action = "BOOKING_CHECKED_OUT", entityType = "booking", actorUserIdArgumentIndex = 1)
    public BookingResponse checkOut(String bookingPublicId, Long staffUserId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElse(null);
        ensureAuthorizedStaffActor(staffUserId, staff, "check out", "booking:check_out");

        applyTransition(booking, BookingStatus.CHECKED_OUT, ActorType.USER, staffUserId, StatusChangeSource.MANUAL, null);
        booking.setCheckedOutBy(staff);

        Booking checkedOutBooking = bookingRepository.saveAndFlush(booking);
        invoiceService.createDraftForCheckout(checkedOutBooking);
        return mapResponse(checkedOutBooking);
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CANCEL)
    @AuditMutation(action = "BOOKING_CANCELLED", entityType = "booking", actorUserIdArgumentIndex = 1)
    public BookingResponse cancel(String bookingPublicId, Long actorUserId, String reason) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        ensureCanCancel(booking, actorUserId);

        applyTransition(booking, BookingStatus.CANCELLED, ActorType.USER, actorUserId, StatusChangeSource.MANUAL, reason);

        Booking cancelledBooking = bookingRepository.saveAndFlush(booking);
        emailService.sendBookingCancelledEmail(cancelledBooking);
        return mapResponse(cancelledBooking);
    }

    /**
     * PENDING -> CONFIRMED. Meant to be called by the payment integration once a gateway payment
     * is verified (BR-012); there is no end-user permission for this yet, so it is intentionally
     * not wired to a controller endpoint.
     */
    @AuditMutation(action = "BOOKING_CONFIRMED", entityType = "booking")
    public BookingResponse confirm(String bookingPublicId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.CONFIRMED, ActorType.SYSTEM, null,
                StatusChangeSource.PAYMENT_CALLBACK, null
        );
        Booking confirmedBooking = bookingRepository.saveAndFlush(booking);
        emailService.sendBookingConfirmedEmail(confirmedBooking);
        return mapResponse(confirmedBooking);
    }

    /**
     * CONFIRMED -> NO_SHOW. Meant to be called by the end-of-day no-show job (BE-8.4).
     */
    @AuditMutation(action = "BOOKING_NO_SHOW", entityType = "booking")
    public BookingResponse markNoShow(String bookingPublicId) {
        return markNoShow(bookingPublicId, null);
    }

    /** Stores the immutable no-show calculation alongside the status transition. */
    @AuditMutation(action = "BOOKING_NO_SHOW", entityType = "booking")
    public BookingResponse markNoShow(String bookingPublicId, String metadata) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.NO_SHOW, ActorType.SYSTEM, null,
                StatusChangeSource.NO_SHOW_JOB, "Guest did not check in", metadata
        );
        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * PENDING -> EXPIRED. Meant to be called by the hold-expiry job (BE-8.4, QĐ-3) once
     * hold_expires_at has passed without payment.
     */
    @AuditMutation(action = "BOOKING_EXPIRED", entityType = "booking")
    public BookingResponse expire(String bookingPublicId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.EXPIRED, ActorType.SYSTEM, null,
                StatusChangeSource.HOLD_EXPIRY_JOB, "Hold expired before payment"
        );
        if (paymentRepository != null) {
            paymentRepository.expireActivePaymentsByBookingId(
                    booking.getId(),
                    Set.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
            );
        }
        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * BR-005: only the booking's contact customer, or an actor holding booking:cancel_any
     * (staff/admin), may cancel it. The DB triggers cannot express this because "who is calling"
     * is not part of the bookings table.
     */
    private void ensureCanCancel(Booking booking, Long actorUserId) {
        boolean isOwner = booking.getCustomerProfile() != null
                && booking.getCustomerProfile().getUser().getId().equals(actorUserId);
        if (isOwner) {
            return;
        }
        boolean hasCancelAny = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> CANCEL_ANY_AUTHORITY.equals(authority.getAuthority()));
        if (!hasCancelAny) {
            throw new AccessDeniedException(
                    "Only the booking's contact customer or a user with " + CANCEL_ANY_AUTHORITY
                            + " can cancel this booking"
            );
        }
    }

    private void applyTransition(
            Booking booking,
            BookingStatus newStatus,
            ActorType actorType,
            Long changedBy,
            StatusChangeSource source,
            String reason
    ) {
        applyTransition(booking, newStatus, actorType, changedBy, source, reason, null);
    }

    private void applyTransition(
            Booking booking,
            BookingStatus newStatus,
            ActorType actorType,
            Long changedBy,
            StatusChangeSource source,
            String reason,
            String metadata
    ) {
        BookingStatus currentStatus = booking.getStatus();
        if (!isAllowedTransition(currentStatus, newStatus)) {
            throw new BusinessValidationException(
                    "Cannot transition booking from " + currentStatus + " to " + newStatus
            );
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        booking.setStatus(newStatus);
        switch (newStatus) {
            case CONFIRMED -> booking.setConfirmedAt(now);
            case CHECKED_IN -> booking.setCheckedInAt(now);
            case CHECKED_OUT -> booking.setCheckedOutAt(now);
            case CANCELLED -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(changedBy);
                booking.setCancellationReason(reason);
            }
            default -> {
                // NO_SHOW and EXPIRED have no dedicated timestamp column on bookings.
            }
        }

        Long historyChangedBy = actorType == ActorType.SYSTEM ? null : changedBy;
        booking.getStatusHistory().add(
                BookingStatusHistory.builder()
                        .booking(booking)
                        .fromStatus(currentStatus)
                        .toStatus(newStatus)
                        .actorType(actorType)
                        .changedBy(historyChangedBy)
                        .source(source)
                        .reason(reason)
                        .metadata(metadata)
                        .build()
        );
    }

    /**
     * Mirrors DATABASE_DESIGN 8.1 / trg_booking_state_machine. Kept in the application layer too
     * (defense-in-depth, same pattern as BR-002) so callers get a clear 400 instead of waiting for
     * the DB trigger's generic SIGNAL to surface as a 409.
     */
    private boolean isAllowedTransition(BookingStatus from, BookingStatus to) {
        return switch (from) {
            case PENDING -> to == BookingStatus.CONFIRMED
                    || to == BookingStatus.CANCELLED
                    || to == BookingStatus.EXPIRED;
            case CONFIRMED -> to == BookingStatus.CHECKED_IN
                    || to == BookingStatus.CANCELLED
                    || to == BookingStatus.NO_SHOW;
            case CHECKED_IN -> to == BookingStatus.CHECKED_OUT;
            case CHECKED_OUT, CANCELLED, NO_SHOW, EXPIRED -> false;
        };
    }

    private Booking getBookingForUpdate(String publicId) {
        return bookingRepository.findForUpdateByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", publicId));
    }

    private void ensureAllRoomsAssigned(Booking booking) {
        if (booking.getBookingRooms() == null || booking.getBookingRooms().isEmpty()
                || booking.getBookingRooms().stream().anyMatch(bookingRoom -> bookingRoom.getRoom() == null)) {
            throw new BusinessValidationException("All booking rooms must be assigned before check-in");
        }
    }

    private void ensureAuthorizedStaffActor(
            Long userId,
            StaffProfile staff,
            String action,
            String requiredAuthority
    ) {
        if (staff != null) {
            return;
        }
        if (userId == null || SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new BusinessValidationException("Only staff can " + action);
        }
        boolean hasRequiredPermission = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
        if (!hasRequiredPermission) {
            throw new BusinessValidationException("Only staff can " + action);
        }
    }

    private BookingResponse mapResponse(Booking booking) {
        return new BookingResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getSource().getCode(),
                booking.getSourceCommissionPercentSnapshot(),
                booking.getContactName(),
                booking.getContactEmail(),
                booking.getContactPhone(),
                booking.getAdults(),
                booking.getChildren(),
                booking.getRoomsTotal(),
                booking.getTaxTotal(),
                booking.getRoomTaxPercentSnapshot(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getHoldExpiresAt(),
                buildBedSummaries(booking),
                booking.getBookingRooms().stream().map(this::mapRoomResponse).toList(),
                booking.getCreatedAt()
        );
    }

    private BookingRoomResponse mapRoomResponse(BookingRoom bookingRoom) {
        return new BookingRoomResponse(
                bookingRoom.getId(),
                bookingRoom.getRoom().getRoomNumber(),
                bookingRoom.getRoomTypeCodeSnapshot(),
                bookingRoom.getRoomTypeNameSnapshot(),
                bookingRoom.getCheckInDate(),
                bookingRoom.getCheckOutDate(),
                bookingRoom.getStatus(),
                bookingRoom.getGuestCount(),
                bookingRoom.getRoomSubtotal(),
                bookingRoom.getCancellationPolicy() == null ? null : bookingRoom.getCancellationPolicy().getCode(),
                bookingRoom.getCancellationPolicy() == null ? null : bookingRoom.getCancellationPolicy().getName(),
                bookingRoom.getPaymentOption(),
                bookingRoom.getPriceAdjustmentPercentSnapshot(),
                bookingRoom.getAssignedAt(),
                bookingRoom.getAssignedBy(),
                bookingRoom.getBookingRoomNights().stream()
                        .sorted(Comparator.comparing(BookingRoomNight::getStayDate))
                        .map(night -> new BookingRoomNightResponse(night.getStayDate(), night.getPrice()))
                .toList()
        );
    }

    private List<BookingBedSummaryResponse> buildBedSummaries(Booking booking) {
        record StateMachineBookingBedSummary(int quantity, java.math.BigDecimal totalAmount) {
        }
        Map<BedType, StateMachineBookingBedSummary> summaries = new EnumMap<>(BedType.class);
        for (BookingRoom bookingRoom : booking.getBookingRooms()) {
            if (bookingRoom.getRoomType() == null || bookingRoom.getRoomType().getBeds() == null) {
                continue;
            }
            for (var bed : bookingRoom.getRoomType().getBeds()) {
                StateMachineBookingBedSummary current = summaries.getOrDefault(
                        bed.getBedType(),
                        new StateMachineBookingBedSummary(0, java.math.BigDecimal.ZERO)
                );
                summaries.put(
                        bed.getBedType(),
                        new StateMachineBookingBedSummary(
                                current.quantity() + bed.getQuantity(),
                                current.totalAmount().add(bookingRoom.getRoomSubtotal())
                        )
                );
            }
        }
        return summaries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new BookingBedSummaryResponse(
                        entry.getKey(),
                        entry.getValue().quantity(),
                        entry.getValue().totalAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                ))
                .toList();
    }
}
