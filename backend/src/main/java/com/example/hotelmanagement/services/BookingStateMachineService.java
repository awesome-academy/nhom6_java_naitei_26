package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomNightResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.BookingStatusHistory;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;

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
    private final Clock clock;

    public BookingStateMachineService(
            BookingRepository bookingRepository,
            StaffProfileRepository staffProfileRepository,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public BookingResponse checkIn(String bookingPublicId, Long staffUserId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElseThrow(() -> new BusinessValidationException("Only staff can check in a booking"));

        applyTransition(booking, BookingStatus.CHECKED_IN, ActorType.USER, staffUserId, StatusChangeSource.MANUAL, null);
        booking.setCheckedInBy(staff);

        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_OUT)
    public BookingResponse checkOut(String bookingPublicId, Long staffUserId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElseThrow(() -> new BusinessValidationException("Only staff can check out a booking"));

        applyTransition(booking, BookingStatus.CHECKED_OUT, ActorType.USER, staffUserId, StatusChangeSource.MANUAL, null);
        booking.setCheckedOutBy(staff);

        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CANCEL)
    public BookingResponse cancel(String bookingPublicId, Long actorUserId, String reason) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        ensureCanCancel(booking, actorUserId);

        applyTransition(booking, BookingStatus.CANCELLED, ActorType.USER, actorUserId, StatusChangeSource.MANUAL, reason);

        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * PENDING -> CONFIRMED. Meant to be called by the payment integration once a gateway payment
     * is verified (BR-012); there is no end-user permission for this yet, so it is intentionally
     * not wired to a controller endpoint.
     */
    public BookingResponse confirm(String bookingPublicId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.CONFIRMED, ActorType.SYSTEM, null,
                StatusChangeSource.PAYMENT_CALLBACK, null
        );
        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * CONFIRMED -> NO_SHOW. Meant to be called by the end-of-day no-show job (BE-8.4).
     */
    public BookingResponse markNoShow(String bookingPublicId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.NO_SHOW, ActorType.SYSTEM, null,
                StatusChangeSource.NO_SHOW_JOB, "Guest did not check in"
        );
        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * PENDING -> EXPIRED. Meant to be called by the hold-expiry job (BE-8.4, QĐ-3) once
     * hold_expires_at has passed without payment.
     */
    public BookingResponse expire(String bookingPublicId) {
        Booking booking = getBookingForUpdate(bookingPublicId);
        applyTransition(
                booking, BookingStatus.EXPIRED, ActorType.SYSTEM, null,
                StatusChangeSource.HOLD_EXPIRY_JOB, "Hold expired before payment"
        );
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
                booking.getCancellationPolicy() == null ? null : booking.getCancellationPolicy().getCode(),
                booking.getHoldExpiresAt(),
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
                bookingRoom.getAssignedAt(),
                bookingRoom.getAssignedBy(),
                bookingRoom.getBookingRoomNights().stream()
                        .sorted(Comparator.comparing(BookingRoomNight::getStayDate))
                        .map(night -> new BookingRoomNightResponse(night.getStayDate(), night.getPrice()))
                        .toList()
        );
    }
}
