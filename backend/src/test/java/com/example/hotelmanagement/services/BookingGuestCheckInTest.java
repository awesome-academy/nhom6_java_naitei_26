package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingCheckInRequest;
import com.example.hotelmanagement.dto.booking.BookingCheckInRoomItem;
import com.example.hotelmanagement.dto.booking.StaffBookingGuestCreateItem;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingGuest;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.IdDocumentType;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingGuestCheckInTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final Long BOOKING_ID = 10L;
    private static final Long STAFF_USER_ID = 42L;

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
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Clock clock;

    @Test
    void replacesPlaceholderGuestsAndKeepsThemAssignedToTheirRooms() {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .status(BookingStatus.CONFIRMED)
                .build();
        booking.setId(BOOKING_ID);

        BookingRoom firstRoom = bookingRoom(101L, "101");
        BookingRoom secondRoom = bookingRoom(202L, "202");
        booking.getBookingRooms().add(firstRoom);
        booking.getBookingRooms().add(secondRoom);
        booking.getBookingGuests().add(BookingGuest.builder()
                .booking(booking)
                .bookingRoom(firstRoom)
                .fullName("Temporary guest")
                .build());

        StaffProfile staff = new StaffProfile();
        staff.setId(77L);
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(cryptoService.encrypt(any())).thenAnswer(invocation ->
                new GuestDocumentCryptoService.EncryptedDocument(
                        invocation.getArgument(0, String.class).getBytes(),
                        new byte[]{1}
                ));

        BookingGuestService service = new BookingGuestService(
                bookingGuestRepository,
                bookingRepository,
                bookingRoomRepository,
                staffProfileRepository,
                auditLogRepository,
                cryptoService,
                objectMapper,
                clock
        );

        service.replaceGuestsForCheckIn(
                BOOKING_PUBLIC_ID,
                new BookingCheckInRequest(List.of(
                        new BookingCheckInRoomItem(
                                firstRoom.getId(),
                                2,
                                List.of(guest("Guest A", "A-001"), guest("Guest B", "B-002"))
                        ),
                        new BookingCheckInRoomItem(
                                secondRoom.getId(),
                                1,
                                List.of(guest("Guest C", "C-003"))
                        )
                )),
                STAFF_USER_ID
        );

        verify(bookingGuestRepository).deleteAllByBookingId(BOOKING_ID);
        ArgumentCaptor<List<BookingGuest>> guestsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookingGuestRepository).saveAllAndFlush(guestsCaptor.capture());
        assertThat(guestsCaptor.getValue())
                .extracting(BookingGuest::getFullName)
                .containsExactly("Guest A", "Guest B", "Guest C");
        assertThat(guestsCaptor.getValue())
                .extracting(guest -> guest.getBookingRoom().getId())
                .containsExactly(firstRoom.getId(), firstRoom.getId(), secondRoom.getId());
        assertThat(firstRoom.getGuestCount()).isEqualTo(2);
        assertThat(secondRoom.getGuestCount()).isEqualTo(1);
        assertThat(booking.getAdults()).isEqualTo(3);
        assertThat(booking.getBookingGuests())
                .extracting(BookingGuest::getFullName)
                .containsExactlyInAnyOrder("Guest A", "Guest B", "Guest C");
    }

    private BookingRoom bookingRoom(Long id, String roomNumber) {
        BookingRoom room = BookingRoom.builder()
                .room(Room.builder().roomNumber(roomNumber).build())
                .build();
        room.setId(id);
        return room;
    }

    private StaffBookingGuestCreateItem guest(String fullName, String documentNumber) {
        return new StaffBookingGuestCreateItem(
                fullName,
                "VN",
                IdDocumentType.NATIONAL_ID,
                documentNumber,
                LocalDate.of(1990, 1, 1)
        );
    }
}
