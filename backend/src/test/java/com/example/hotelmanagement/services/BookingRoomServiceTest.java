package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingRoomAssignmentResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeRequest;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRoomNightRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRoomServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String BOOKING_PUBLIC_ID = "booking-public-id";
    private static final Long BOOKING_ROOM_ID = 55L;
    private static final Long NEW_BOOKING_ROOM_ID = 77L;
    private static final Long STAFF_USER_ID = 99L;
    private static final Long STAFF_PROFILE_ID = 12L;
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDate MOVE_DATE = LocalDate.of(2026, 8, 17);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 8, 19);

    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private BookingRoomNightRepository bookingRoomNightRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomStatusBlockRepository roomStatusBlockRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;

    private BookingRoomService service;

    @BeforeEach
    void setUp() {
        service = new BookingRoomService(
                bookingRoomRepository,
                bookingRoomNightRepository,
                roomRepository,
                roomStatusBlockRepository,
                staffProfileRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void assignRoomStoresAssignmentTimestampAndStaffProfile() {
        StaffProfile staff = createStaffProfile();
        BookingRoom bookingRoom = createBookingRoom(BookingStatus.CONFIRMED, BookingRoomStatus.RESERVED);
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(bookingRoom));
        when(bookingRoomRepository.saveAndFlush(any(BookingRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingRoomAssignmentResponse response = service.assignRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                STAFF_USER_ID
        );

        assertThat(bookingRoom.getAssignedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(bookingRoom.getAssignedBy()).isEqualTo(STAFF_PROFILE_ID);
        assertThat(response.bookingRoomId()).isEqualTo(BOOKING_ROOM_ID);
        assertThat(response.assignedByStaffId()).isEqualTo(STAFF_PROFILE_ID);
        assertThat(response.assignedByEmployeeCode()).isEqualTo("EMP-0001");
        verify(bookingRoomRepository).saveAndFlush(bookingRoom);
    }

    @Test
    void assignRoomAllowsCheckedInOccupiedBookingRoom() {
        StaffProfile staff = createStaffProfile();
        BookingRoom bookingRoom = createBookingRoom(BookingStatus.CHECKED_IN, BookingRoomStatus.OCCUPIED);
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(bookingRoom));
        when(bookingRoomRepository.saveAndFlush(any(BookingRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingRoomAssignmentResponse response = service.assignRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                STAFF_USER_ID
        );

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(response.roomStatus()).isEqualTo(BookingRoomStatus.OCCUPIED);
        assertThat(response.assignedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(response.assignedByStaffId()).isEqualTo(STAFF_PROFILE_ID);
    }

    @Test
    void assignRoomRejectsWhenActorHasNoStaffProfile() {
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoom(BOOKING_PUBLIC_ID, BOOKING_ROOM_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Only staff can assign rooms");
        verify(bookingRoomRepository, never()).findForUpdateByIdAndBookingPublicId(any(), any());
        verify(bookingRoomRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRoomThrowsWhenBookingRoomNotFoundInBooking() {
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(createStaffProfile()));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoom(BOOKING_PUBLIC_ID, BOOKING_ROOM_ID, STAFF_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bookingRoomRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRoomRejectsBookingThatIsNotConfirmed() {
        stubAssignableDependencies(createBookingRoom(BookingStatus.PENDING, BookingRoomStatus.RESERVED));

        assertThatThrownBy(() -> service.assignRoom(BOOKING_PUBLIC_ID, BOOKING_ROOM_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Only confirmed reserved rooms or checked-in occupied rooms can be assigned");
        verify(bookingRoomRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRoomRejectsBookingRoomThatIsNotReserved() {
        stubAssignableDependencies(createBookingRoom(BookingStatus.CHECKED_IN, BookingRoomStatus.RESERVED));

        assertThatThrownBy(() -> service.assignRoom(BOOKING_PUBLIC_ID, BOOKING_ROOM_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Only confirmed reserved rooms or checked-in occupied rooms can be assigned");
        verify(bookingRoomRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRoomRejectsAlreadyAssignedBookingRoom() {
        BookingRoom bookingRoom = createBookingRoom(BookingStatus.CONFIRMED, BookingRoomStatus.RESERVED);
        bookingRoom.setAssignedAt(OffsetDateTime.now(FIXED_CLOCK).minusMinutes(5));
        bookingRoom.setAssignedBy(STAFF_PROFILE_ID);
        stubAssignableDependencies(bookingRoom);

        assertThatThrownBy(() -> service.assignRoom(BOOKING_PUBLIC_ID, BOOKING_ROOM_ID, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Booking room has already been assigned");
        verify(bookingRoomRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRoomCreatesMovedLineTransfersCommittedNightsAndMovesPreviousLineOut() {
        StaffProfile staff = createStaffProfile();
        BookingRoom previousBookingRoom = createBookingRoom(BookingStatus.CHECKED_IN, BookingRoomStatus.OCCUPIED);
        Room newRoom = createRoom(2L, "B202", createRoomType(20L, "STE", "Suite"));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(previousBookingRoom));
        when(roomRepository.findOperationalForUpdateByRoomNumber("B202")).thenReturn(Optional.of(newRoom));
        when(bookingRoomRepository.existsOverlappingBookingExcludingId(
                eq(newRoom.getId()),
                eq(BOOKING_ROOM_ID),
                any(),
                eq(MOVE_DATE),
                eq(CHECK_OUT_DATE)
        )).thenReturn(false);
        when(roomStatusBlockRepository.existsOverlappingBlock(newRoom.getId(), MOVE_DATE, CHECK_OUT_DATE))
                .thenReturn(false);
        when(bookingRoomRepository.saveAndFlush(any(BookingRoom.class))).thenAnswer(invocation -> {
            BookingRoom bookingRoom = invocation.getArgument(0);
            if (bookingRoom.getId() == null) {
                bookingRoom.setId(NEW_BOOKING_ROOM_ID);
            }
            return bookingRoom;
        });
        when(bookingRoomNightRepository.transferNightsFromDate(
                eq(previousBookingRoom),
                any(BookingRoom.class),
                eq(MOVE_DATE)
        )).thenReturn(2);

        BookingRoomChangeResponse response = service.changeRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                new BookingRoomChangeRequest("b202", MOVE_DATE),
                STAFF_USER_ID
        );

        ArgumentCaptor<BookingRoom> savedBookingRoom = ArgumentCaptor.forClass(BookingRoom.class);
        verify(bookingRoomRepository, times(2)).saveAndFlush(savedBookingRoom.capture());
        BookingRoom newBookingRoom = savedBookingRoom.getAllValues().getFirst();

        assertThat(newBookingRoom.getBooking()).isSameAs(previousBookingRoom.getBooking());
        assertThat(newBookingRoom.getRoom()).isSameAs(newRoom);
        assertThat(newBookingRoom.getRoomTypeCodeSnapshot()).isEqualTo("STE");
        assertThat(newBookingRoom.getCheckInDate()).isEqualTo(MOVE_DATE);
        assertThat(newBookingRoom.getCheckOutDate()).isEqualTo(CHECK_OUT_DATE);
        assertThat(newBookingRoom.getStatus()).isEqualTo(BookingRoomStatus.OCCUPIED);
        assertThat(newBookingRoom.getMovedFromBookingRoomId()).isEqualTo(BOOKING_ROOM_ID);
        assertThat(newBookingRoom.getAssignedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(newBookingRoom.getAssignedBy()).isEqualTo(STAFF_PROFILE_ID);
        assertThat(previousBookingRoom.getCheckOutDate()).isEqualTo(MOVE_DATE);
        assertThat(previousBookingRoom.getStatus()).isEqualTo(BookingRoomStatus.MOVED_OUT);
        assertThat(response.newBookingRoomId()).isEqualTo(NEW_BOOKING_ROOM_ID);
        assertThat(response.previousRoomStatus()).isEqualTo(BookingRoomStatus.MOVED_OUT);
        assertThat(response.transferredNightCount()).isEqualTo(2);
    }

    @Test
    void changeRoomRejectsWhenBookingIsNotCheckedIn() {
        StaffProfile staff = createStaffProfile();
        BookingRoom previousBookingRoom = createBookingRoom(BookingStatus.CONFIRMED, BookingRoomStatus.RESERVED);
        Room newRoom = createRoom(2L, "B202", createRoomType(20L, "STE", "Suite"));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(previousBookingRoom));
        when(roomRepository.findOperationalForUpdateByRoomNumber("B202")).thenReturn(Optional.of(newRoom));

        assertThatThrownBy(() -> service.changeRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                new BookingRoomChangeRequest("B202", MOVE_DATE),
                STAFF_USER_ID
        ))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Only checked-in bookings can change rooms");
        verify(bookingRoomNightRepository, never()).transferNightsFromDate(any(), any(), any());
    }

    @Test
    void changeRoomRejectsMoveDateOutsideCurrentStay() {
        StaffProfile staff = createStaffProfile();
        BookingRoom previousBookingRoom = createBookingRoom(BookingStatus.CHECKED_IN, BookingRoomStatus.OCCUPIED);
        Room newRoom = createRoom(2L, "B202", createRoomType(20L, "STE", "Suite"));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(previousBookingRoom));
        when(roomRepository.findOperationalForUpdateByRoomNumber("B202")).thenReturn(Optional.of(newRoom));

        assertThatThrownBy(() -> service.changeRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                new BookingRoomChangeRequest("B202", CHECK_OUT_DATE),
                STAFF_USER_ID
        ))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Move date must be within the current stay");
        verify(bookingRoomNightRepository, never()).transferNightsFromDate(any(), any(), any());
    }

    @Test
    void changeRoomRejectsWhenNewRoomHasConflict() {
        StaffProfile staff = createStaffProfile();
        BookingRoom previousBookingRoom = createBookingRoom(BookingStatus.CHECKED_IN, BookingRoomStatus.OCCUPIED);
        Room newRoom = createRoom(2L, "B202", createRoomType(20L, "STE", "Suite"));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(previousBookingRoom));
        when(roomRepository.findOperationalForUpdateByRoomNumber("B202")).thenReturn(Optional.of(newRoom));
        when(bookingRoomRepository.existsOverlappingBookingExcludingId(
                eq(newRoom.getId()),
                eq(BOOKING_ROOM_ID),
                any(),
                eq(MOVE_DATE),
                eq(CHECK_OUT_DATE)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.changeRoom(
                BOOKING_PUBLIC_ID,
                BOOKING_ROOM_ID,
                new BookingRoomChangeRequest("B202", MOVE_DATE),
                STAFF_USER_ID
        ))
                .isInstanceOf(com.example.hotelmanagement.exceptions.BookingRoomConflictException.class)
                .hasMessage("New room has an active booking in the requested date range");
        verify(bookingRoomNightRepository, never()).transferNightsFromDate(any(), any(), any());
    }

    private void stubAssignableDependencies(BookingRoom bookingRoom) {
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(createStaffProfile()));
        when(bookingRoomRepository.findForUpdateByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(bookingRoom));
    }

    private BookingRoom createBookingRoom(BookingStatus bookingStatus, BookingRoomStatus bookingRoomStatus) {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000123")
                .status(bookingStatus)
                .build();
        Room room = createRoom(1L, "A101", createRoomType(10L, "DLX", "Deluxe"));
        BookingRoom bookingRoom = BookingRoom.builder()
                .booking(booking)
                .room(room)
                .roomType(room.getRoomType())
                .roomTypeCodeSnapshot(room.getRoomType().getCode())
                .roomTypeNameSnapshot(room.getRoomType().getName())
                .checkInDate(CHECK_IN_DATE)
                .checkOutDate(CHECK_OUT_DATE)
                .guestCount(2)
                .status(bookingRoomStatus)
                .build();
        bookingRoom.setId(BOOKING_ROOM_ID);
        return bookingRoom;
    }

    private Room createRoom(Long id, String roomNumber, RoomType roomType) {
        Room room = Room.builder()
                .roomNumber(roomNumber)
                .roomType(roomType)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .isActive(true)
                .build();
        room.setId(id);
        return room;
    }

    private RoomType createRoomType(Long id, String code, String name) {
        RoomType roomType = RoomType.builder()
                .code(code)
                .name(name)
                .maxOccupancy(2)
                .isActive(true)
                .build();
        roomType.setId(id);
        return roomType;
    }

    private StaffProfile createStaffProfile() {
        User user = User.builder()
                .email("staff@example.com")
                .fullName("Staff")
                .build();
        user.setId(STAFF_USER_ID);
        StaffProfile staff = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .build();
        staff.setId(STAFF_PROFILE_ID);
        return staff;
    }
}
