package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.bookingguest.BookingGuestCreateRequest;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestIdentityDocumentResponse;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestResponse;
import com.example.hotelmanagement.entity.AuditLog;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingGuest;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.IdDocumentType;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingGuestServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String BOOKING_PUBLIC_ID = "booking-public-id";
    private static final Long BOOKING_ID = 10L;
    private static final Long BOOKING_ROOM_ID = 20L;
    private static final Long GUEST_ID = 30L;
    private static final Long STAFF_USER_ID = 40L;

    @Mock
    private BookingGuestRepository bookingGuestRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private GuestDocumentCryptoService cryptoService;

    private BookingGuestService service;

    @BeforeEach
    void setUp() {
        service = new BookingGuestService(
                bookingGuestRepository,
                bookingRepository,
                bookingRoomRepository,
                staffProfileRepository,
                auditLogRepository,
                cryptoService,
                new ObjectMapper(),
                FIXED_CLOCK
        );
    }

    @Test
    void addGuestStoresEncryptedDocumentAndReturnsMaskedResponse() {
        Booking booking = createBooking(BookingStatus.CONFIRMED);
        BookingRoom bookingRoom = createBookingRoom(booking);
        byte[] ciphertext = new byte[] {1, 2, 3};
        byte[] lookupHash = new byte[] {4, 5, 6};
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(createStaffProfile()));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(bookingRoomRepository.findByIdAndBookingPublicId(BOOKING_ROOM_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(bookingRoom));
        when(cryptoService.encrypt("012345678901"))
                .thenReturn(new GuestDocumentCryptoService.EncryptedDocument(ciphertext, lookupHash));
        when(bookingGuestRepository.saveAndFlush(any(BookingGuest.class))).thenAnswer(invocation -> {
            BookingGuest guest = invocation.getArgument(0);
            guest.setId(GUEST_ID);
            return guest;
        });

        BookingGuestResponse response = service.addGuest(
                BOOKING_PUBLIC_ID,
                new BookingGuestCreateRequest(
                        BOOKING_ROOM_ID,
                        " Nguyen Van A ",
                        "vn",
                        IdDocumentType.NATIONAL_ID,
                        "012345678901",
                        LocalDate.of(1990, 1, 2)
                ),
                STAFF_USER_ID
        );

        ArgumentCaptor<BookingGuest> guestCaptor = ArgumentCaptor.forClass(BookingGuest.class);
        verify(bookingGuestRepository).saveAndFlush(guestCaptor.capture());
        BookingGuest savedGuest = guestCaptor.getValue();

        assertThat(savedGuest.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(savedGuest.getNationality()).isEqualTo("VN");
        assertThat(savedGuest.getIdDocumentNumberEncrypted()).isSameAs(ciphertext);
        assertThat(savedGuest.getIdDocumentLookupHash()).isSameAs(lookupHash);
        assertThat(response.id()).isEqualTo(GUEST_ID);
        assertThat(response.hasIdDocument()).isTrue();
        assertThat(response.roomNumber()).isEqualTo("A101");
    }

    @Test
    void addGuestAllowsGuestBeforeRoomAssignment() {
        Booking booking = createBooking(BookingStatus.PENDING);
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(createStaffProfile()));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(bookingGuestRepository.saveAndFlush(any(BookingGuest.class))).thenAnswer(invocation -> {
            BookingGuest guest = invocation.getArgument(0);
            guest.setId(GUEST_ID);
            return guest;
        });

        BookingGuestResponse response = service.addGuest(
                BOOKING_PUBLIC_ID,
                new BookingGuestCreateRequest(
                        null,
                        "Tran Thi B",
                        null,
                        null,
                        null,
                        null
                ),
                STAFF_USER_ID
        );

        assertThat(response.bookingRoomId()).isNull();
        assertThat(response.hasIdDocument()).isFalse();
        verify(bookingRoomRepository, never()).findByIdAndBookingPublicId(any(), any());
        verify(cryptoService, never()).encrypt(any());
    }

    @Test
    void addGuestRejectsDocumentTypeWithoutNumber() {
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(createStaffProfile()));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(createBooking(BookingStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.addGuest(
                BOOKING_PUBLIC_ID,
                new BookingGuestCreateRequest(
                        null,
                        "Nguyen Van A",
                        "VN",
                        IdDocumentType.PASSPORT,
                        " ",
                        null
                ),
                STAFF_USER_ID
        ))
                .isInstanceOf(com.example.hotelmanagement.exceptions.BusinessValidationException.class)
                .hasMessage("Guest identity document type and number must be provided together");
        verify(bookingGuestRepository, never()).saveAndFlush(any());
    }

    @Test
    void getGuestsDoesNotRevealDocumentNumber() {
        BookingGuest guest = createGuestWithDocument();
        when(bookingRepository.existsByPublicId(BOOKING_PUBLIC_ID)).thenReturn(true);
        when(bookingGuestRepository.findAllByBooking_PublicIdOrderByIdAsc(BOOKING_PUBLIC_ID))
                .thenReturn(List.of(guest));

        List<BookingGuestResponse> responses = service.getGuests(BOOKING_PUBLIC_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().hasIdDocument()).isTrue();
        verify(cryptoService, never()).decrypt(any());
    }

    @Test
    void revealIdentityDocumentDecryptsAndWritesAuditLog() {
        BookingGuest guest = createGuestWithDocument();
        when(bookingGuestRepository.findByIdAndBooking_PublicId(GUEST_ID, BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(guest));
        when(cryptoService.decrypt(guest.getIdDocumentNumberEncrypted())).thenReturn("012345678901");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingGuestIdentityDocumentResponse response = service.revealIdentityDocument(
                BOOKING_PUBLIC_ID,
                GUEST_ID,
                STAFF_USER_ID,
                "127.0.0.1",
                "PostmanRuntime"
        );

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        AuditLog auditLog = auditLogCaptor.getValue();

        assertThat(response.idDocumentNumber()).isEqualTo("012345678901");
        assertThat(response.accessedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(auditLog.getActorUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(auditLog.getAction()).isEqualTo("GUEST_ID_DOCUMENT_READ");
        assertThat(auditLog.getEntityType()).isEqualTo("booking_guest");
        assertThat(auditLog.getEntityId()).isEqualTo(GUEST_ID);
        assertThat(auditLog.getAfterData()).contains(BOOKING_PUBLIC_ID);
    }

    private Booking createBooking(BookingStatus status) {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000001")
                .status(status)
                .build();
        booking.setId(BOOKING_ID);
        return booking;
    }

    private BookingRoom createBookingRoom(Booking booking) {
        Room room = Room.builder()
                .roomNumber("A101")
                .build();
        room.setId(1L);
        BookingRoom bookingRoom = BookingRoom.builder()
                .booking(booking)
                .room(room)
                .status(BookingRoomStatus.RESERVED)
                .build();
        bookingRoom.setId(BOOKING_ROOM_ID);
        return bookingRoom;
    }

    private BookingGuest createGuestWithDocument() {
        Booking booking = createBooking(BookingStatus.CONFIRMED);
        BookingGuest guest = BookingGuest.builder()
                .booking(booking)
                .bookingRoom(createBookingRoom(booking))
                .fullName("Nguyen Van A")
                .nationality("VN")
                .idDocumentType(IdDocumentType.NATIONAL_ID)
                .idDocumentNumberEncrypted(new byte[] {1, 2, 3})
                .idDocumentLookupHash(new byte[] {4, 5, 6})
                .dateOfBirth(LocalDate.of(1990, 1, 2))
                .build();
        guest.setId(GUEST_ID);
        return guest;
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
        staff.setId(50L);
        return staff;
    }
}
