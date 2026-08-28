package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.booking.StaffBookingCreateRequest;
import com.example.hotelmanagement.dto.booking.StaffBookingGuestCreateItem;
import com.example.hotelmanagement.dto.booking.StaffBookingRoomCreateItem;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.IdDocumentType;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomNightRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.BookingSourceRepository;
import com.example.hotelmanagement.repositories.BookingStatusHistoryRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.PaymentEventRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RefundRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffBookingServiceTest {

    private static final Long STAFF_ID = 42L;
    private static final Long STAFF_PROFILE_ID = 77L;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 9, 3);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingGuestRepository bookingGuestRepository;
    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private BookingSourceRepository bookingSourceRepository;
    @Mock
    private BookingRoomNightRepository bookingRoomNightRepository;
    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BookingCalculatorService bookingCalculatorService;
    @Mock
    private CancellationPolicyService cancellationPolicyService;
    @Mock
    private BookingOptionResolverService bookingOptionResolverService;
    @Mock
    private EmailService emailService;
    @Mock
    private GuestDocumentCryptoService guestDocumentCryptoService;
    @Mock
    private Clock clock;
    @Mock
    private RoomStatusBlockRepository roomStatusBlockRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createsPendingStaffBookingWithSelectedRoomAndGuestCounts() {
        RoomType roomType = RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .maxOccupancy(3)
                .maxAdults(3)
                .maxChildren(0)
                .basePrice(new BigDecimal("1500000"))
                .isActive(true)
                .build();
        Room room = Room.builder()
                .roomType(roomType)
                .roomNumber("A101")
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .isActive(true)
                .build();
        room.setId(10L);
        CancellationPolicy policy = CancellationPolicy.builder()
                .code("NON_REFUND")
                .name("Non-refundable")
                .isActive(true)
                .build();
        BookingSource source = BookingSource.builder()
                .code("STAFF_MANUAL")
                .name("Staff manual")
                .commissionPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        StaffProfile staffProfile = StaffProfile.builder()
                .employeeCode("EMP-0001")
                .build();
        staffProfile.setId(STAFF_PROFILE_ID);

        when(clock.instant()).thenReturn(Instant.parse("2026-08-20T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("STAFF_MANUAL"))
                .thenReturn(Optional.of(source));
        when(staffProfileRepository.findByUser_Id(STAFF_ID)).thenReturn(Optional.of(staffProfile));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));
        when(bookingRoomRepository.existsOverlappingBooking(
                eq(10L), any(Set.class), eq(CHECK_IN), eq(CHECK_OUT)
        )).thenReturn(false);
        when(roomStatusBlockRepository.existsOverlappingBlock(10L, CHECK_IN, CHECK_OUT))
                .thenReturn(false);
        when(bookingOptionResolverService.resolveStaffBooking(
                "DLX", BookingPaymentOption.ONLINE
        )).thenReturn(new BookingOptionSelection(roomType, BookingPaymentOption.ONLINE, policy, BigDecimal.ZERO));
        when(bookingCalculatorService.calculateStaffPrice(any(BookingPriceCalculationRequest.class)))
                .thenReturn(new BookingPriceCalculationResponse(
                        null,
                        1L,
                        "DLX",
                        BookingPaymentOption.ONLINE,
                        "NON_REFUND",
                        "Non-refundable",
                        BigDecimal.ZERO,
                        CHECK_IN,
                        CHECK_OUT,
                        2,
                        2,
                        0,
                        List.of(
                                new DailyRateResponse(CHECK_IN, new BigDecimal("1500000")),
                                new DailyRateResponse(CHECK_IN.plusDays(1), new BigDecimal("1500000"))
                        ),
                        new BigDecimal("3000000"),
                        new BigDecimal("10"),
                        new BigDecimal("300000"),
                        new BigDecimal("3300000"),
                        "VND"
                ));
        when(bookingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(guestDocumentCryptoService.encrypt(anyString()))
                .thenReturn(new GuestDocumentCryptoService.EncryptedDocument(new byte[]{1}, new byte[]{2}));

        StaffBookingCreateRequest request = new StaffBookingCreateRequest(
                "Nguyen Van A",
                "guest@example.com",
                "0900000000",
                null,
                List.of(new StaffBookingRoomCreateItem(
                        "A101", "DLX", BookingPaymentOption.ONLINE,
                        CHECK_IN, CHECK_OUT, 2,
                        List.of(
                                new StaffBookingGuestCreateItem(
                                        "Nguyen Van A", "VN", IdDocumentType.NATIONAL_ID, "012345678901", null
                                ),
                                new StaffBookingGuestCreateItem(
                                        "Nguyen Van B", "VN", IdDocumentType.PASSPORT, "P1234567", null
                                )
                        )
                ))
        );

        var response = bookingService.createStaffBooking(request, STAFF_ID);

        assertThat(response.status()).isEqualTo(com.example.hotelmanagement.entity.enums.BookingStatus.PENDING);
        assertThat(response.sourceCode()).isEqualTo("STAFF_MANUAL");
        assertThat(response.rooms()).singleElement().satisfies(roomResponse -> {
            assertThat(roomResponse.roomNumber()).isEqualTo("A101");
            assertThat(roomResponse.guestCount()).isEqualTo(2);
            assertThat(roomResponse.assignedByStaffId()).isEqualTo(STAFF_PROFILE_ID);
        });
        assertThat(response.adults()).isEqualTo(2);
        assertThat(response.children()).isZero();
        verify(guestDocumentCryptoService).encrypt("012345678901");
        verify(guestDocumentCryptoService).encrypt("P1234567");
        verify(bookingOptionResolverService).resolveStaffBooking("DLX", BookingPaymentOption.ONLINE);
        ArgumentCaptor<BookingPriceCalculationRequest> priceRequestCaptor =
                ArgumentCaptor.forClass(BookingPriceCalculationRequest.class);
        verify(bookingCalculatorService).calculateStaffPrice(priceRequestCaptor.capture());
        assertThat(priceRequestCaptor.getValue().cancellationPolicyCode()).isEqualTo("NON_REFUND");
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getBookingGuests())
                .extracting(guest -> guest.getFullName())
                .containsExactlyInAnyOrder("Nguyen Van A", "Nguyen Van B");
        verify(roomStatusBlockRepository).existsOverlappingBlock(10L, CHECK_IN, CHECK_OUT);
        verify(staffProfileRepository).findByUser_Id(STAFF_ID);
    }
}
