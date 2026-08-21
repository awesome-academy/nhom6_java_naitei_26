package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingRoomAssignmentResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeRequest;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BookingRoomConflictException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRoomNightRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

@Service
@Validated
@Transactional
public class BookingRoomService {

    private static final Logger log = LoggerFactory.getLogger(BookingRoomService.class);
    private static final Set<BookingRoomStatus> ACTIVE_BOOKING_ROOM_STATUSES =
            Set.of(BookingRoomStatus.RESERVED, BookingRoomStatus.OCCUPIED);

    private final BookingRoomRepository bookingRoomRepository;
    private final BookingRoomNightRepository bookingRoomNightRepository;
    private final RoomRepository roomRepository;
    private final RoomStatusBlockRepository roomStatusBlockRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final Clock clock;

    public BookingRoomService(
            BookingRoomRepository bookingRoomRepository,
            BookingRoomNightRepository bookingRoomNightRepository,
            RoomRepository roomRepository,
            RoomStatusBlockRepository roomStatusBlockRepository,
            StaffProfileRepository staffProfileRepository,
            Clock clock
    ) {
        this.bookingRoomRepository = bookingRoomRepository;
        this.bookingRoomNightRepository = bookingRoomNightRepository;
        this.roomRepository = roomRepository;
        this.roomStatusBlockRepository = roomStatusBlockRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public BookingRoomAssignmentResponse assignRoom(
            String bookingPublicId,
            Long bookingRoomId,
            Long staffUserId
    ) {
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElseThrow(() -> new BusinessValidationException("Only staff can assign rooms"));
        BookingRoom bookingRoom = bookingRoomRepository
                .findForUpdateByIdAndBookingPublicId(bookingRoomId, bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking room", bookingRoomId.toString()));

        ensureAssignable(bookingRoom);

        bookingRoom.setAssignedAt(OffsetDateTime.now(clock));
        bookingRoom.setAssignedBy(staff.getId());

        return mapResponse(bookingRoomRepository.saveAndFlush(bookingRoom), staff);
    }

    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public BookingRoomChangeResponse changeRoom(
            String bookingPublicId,
            Long bookingRoomId,
            @Valid BookingRoomChangeRequest request,
            Long staffUserId
    ) {
        StaffProfile staff = staffProfileRepository.findByUser_Id(staffUserId)
                .orElseThrow(() -> new BusinessValidationException("Only staff can change rooms"));
        BookingRoom previousBookingRoom = bookingRoomRepository
                .findForUpdateByIdAndBookingPublicId(bookingRoomId, bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking room", bookingRoomId.toString()));
        Room newRoom = getAvailableRoomForChange(request.newRoomNumber());

        ensureChangeable(previousBookingRoom, newRoom, request.moveDate());

        BookingRoom newBookingRoom = buildMovedBookingRoom(previousBookingRoom, newRoom, request.moveDate(), staff);
        try {
            newBookingRoom = bookingRoomRepository.saveAndFlush(newBookingRoom);
            int transferredNightCount = bookingRoomNightRepository.transferNightsFromDate(
                    previousBookingRoom,
                    newBookingRoom,
                    request.moveDate()
            );
            ensureAllRemainingNightsTransferred(previousBookingRoom, request.moveDate(), transferredNightCount);

            previousBookingRoom.setCheckOutDate(request.moveDate());
            previousBookingRoom.setStatus(BookingRoomStatus.MOVED_OUT);
            bookingRoomRepository.saveAndFlush(previousBookingRoom);

            return mapChangeResponse(previousBookingRoom, newBookingRoom, request.moveDate(), transferredNightCount);
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Database rejected room change bookingPublicId={} previousBookingRoomId={} newRoomNumber={}",
                    bookingPublicId,
                    bookingRoomId,
                    newRoom.getRoomNumber(),
                    exception
            );
            throw new BookingRoomConflictException(
                    "Room change conflicts with existing booking room data",
                    exception
            );
        }
    }

    private void ensureAssignable(BookingRoom bookingRoom) {
        Booking booking = bookingRoom.getBooking();
        if (bookingRoom.getAssignedAt() != null || bookingRoom.getAssignedBy() != null) {
            throw new BusinessValidationException("Booking room has already been assigned");
        }
        boolean assignBeforeCheckIn = booking.getStatus() == BookingStatus.CONFIRMED
                && bookingRoom.getStatus() == BookingRoomStatus.RESERVED;
        boolean assignDuringCheckIn = booking.getStatus() == BookingStatus.CHECKED_IN
                && bookingRoom.getStatus() == BookingRoomStatus.OCCUPIED;
        if (!assignBeforeCheckIn && !assignDuringCheckIn) {
            throw new BusinessValidationException(
                    "Only confirmed reserved rooms or checked-in occupied rooms can be assigned"
            );
        }
    }

    private Room getAvailableRoomForChange(String newRoomNumber) {
        String normalizedRoomNumber = normalizeRoomNumber(newRoomNumber);
        Room room = roomRepository.findOperationalForUpdateByRoomNumber(normalizedRoomNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Room", normalizedRoomNumber));
        if (!Boolean.TRUE.equals(room.getIsActive())) {
            throw new ResourceNotFoundException("Room", normalizedRoomNumber);
        }
        return room;
    }

    private void ensureChangeable(BookingRoom previousBookingRoom, Room newRoom, LocalDate moveDate) {
        Booking booking = previousBookingRoom.getBooking();
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessValidationException("Only checked-in bookings can change rooms");
        }
        if (previousBookingRoom.getStatus() != BookingRoomStatus.OCCUPIED) {
            throw new BusinessValidationException("Only occupied booking rooms can be changed");
        }
        if (moveDate == null) {
            throw new BusinessValidationException("Move date is required");
        }
        if (!moveDate.isAfter(previousBookingRoom.getCheckInDate())
                || !moveDate.isBefore(previousBookingRoom.getCheckOutDate())) {
            throw new BusinessValidationException("Move date must be within the current stay");
        }
        if (previousBookingRoom.getRoom().getId().equals(newRoom.getId())) {
            throw new BusinessValidationException("New room must be different from the current room");
        }
        if (newRoom.getOperationalStatus() != RoomOperationalStatus.ACTIVE) {
            throw new BusinessValidationException("New room must be operationally active");
        }
        RoomType newRoomType = newRoom.getRoomType();
        if (!Boolean.TRUE.equals(newRoomType.getIsActive()) || newRoomType.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Room", newRoom.getRoomNumber());
        }
        ensureCapacity(previousBookingRoom, newRoom);
        ensureNewRoomHasNoConflict(newRoom, moveDate, previousBookingRoom.getCheckOutDate(), previousBookingRoom.getId());
    }

    private void ensureCapacity(BookingRoom previousBookingRoom, Room newRoom) {
        int maxOccupancy = newRoom.getMaxOccupancyOverride() == null
                ? newRoom.getRoomType().getMaxOccupancy()
                : newRoom.getMaxOccupancyOverride();
        if (previousBookingRoom.getGuestCount() > maxOccupancy) {
            throw new BusinessValidationException("New room cannot fit the booking guest count");
        }
    }

    private void ensureNewRoomHasNoConflict(
            Room newRoom,
            LocalDate startDate,
            LocalDate endDate,
            Long excludedBookingRoomId
    ) {
        if (bookingRoomRepository.existsOverlappingBookingExcludingId(
                newRoom.getId(),
                excludedBookingRoomId,
                ACTIVE_BOOKING_ROOM_STATUSES,
                startDate,
                endDate
        )) {
            throw new BookingRoomConflictException("New room has an active booking in the requested date range");
        }
        if (roomStatusBlockRepository.existsOverlappingBlock(newRoom.getId(), startDate, endDate)) {
            throw new BookingRoomConflictException("New room has a status block in the requested date range");
        }
    }

    private BookingRoom buildMovedBookingRoom(
            BookingRoom previousBookingRoom,
            Room newRoom,
            LocalDate moveDate,
            StaffProfile staff
    ) {
        RoomType newRoomType = newRoom.getRoomType();
        return BookingRoom.builder()
                .booking(previousBookingRoom.getBooking())
                .room(newRoom)
                .roomType(newRoomType)
                .roomTypeCodeSnapshot(newRoomType.getCode())
                .roomTypeNameSnapshot(newRoomType.getName())
                .checkInDate(moveDate)
                .checkOutDate(previousBookingRoom.getCheckOutDate())
                .status(BookingRoomStatus.OCCUPIED)
                .guestCount(previousBookingRoom.getGuestCount())
                .movedFromBookingRoomId(previousBookingRoom.getId())
                .assignedAt(OffsetDateTime.now(clock))
                .assignedBy(staff.getId())
                .build();
    }

    private void ensureAllRemainingNightsTransferred(
            BookingRoom previousBookingRoom,
            LocalDate moveDate,
            int transferredNightCount
    ) {
        long expectedNightCount = ChronoUnit.DAYS.between(moveDate, previousBookingRoom.getCheckOutDate());
        if (transferredNightCount != expectedNightCount) {
            throw new BusinessValidationException("Cannot change room because booking room nights are incomplete");
        }
    }

    private String normalizeRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessValidationException("Room number cannot be blank");
        }
        return roomNumber.strip().toUpperCase(Locale.ROOT);
    }

    private BookingRoomAssignmentResponse mapResponse(BookingRoom bookingRoom, StaffProfile staff) {
        Booking booking = bookingRoom.getBooking();
        return new BookingRoomAssignmentResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                bookingRoom.getId(),
                bookingRoom.getRoom().getRoomNumber(),
                bookingRoom.getStatus(),
                bookingRoom.getAssignedAt(),
                bookingRoom.getAssignedBy(),
                staff.getEmployeeCode()
        );
    }

    private BookingRoomChangeResponse mapChangeResponse(
            BookingRoom previousBookingRoom,
            BookingRoom newBookingRoom,
            LocalDate moveDate,
            int transferredNightCount
    ) {
        Booking booking = previousBookingRoom.getBooking();
        return new BookingRoomChangeResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                moveDate,
                previousBookingRoom.getId(),
                previousBookingRoom.getRoom().getRoomNumber(),
                previousBookingRoom.getStatus(),
                previousBookingRoom.getCheckInDate(),
                previousBookingRoom.getCheckOutDate(),
                newBookingRoom.getId(),
                newBookingRoom.getRoom().getRoomNumber(),
                newBookingRoom.getStatus(),
                newBookingRoom.getCheckInDate(),
                newBookingRoom.getCheckOutDate(),
                newBookingRoom.getMovedFromBookingRoomId(),
                newBookingRoom.getAssignedAt(),
                newBookingRoom.getAssignedBy(),
                transferredNightCount
        );
    }
}
