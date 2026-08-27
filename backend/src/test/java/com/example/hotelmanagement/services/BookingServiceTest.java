package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingCreateRequest;
import com.example.hotelmanagement.dto.booking.BookingDetailResponse;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomCreateItem;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingGuest;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.BookingStatusHistory;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BookingRoomConflictException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.BookingSourceRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final Long USER_ID = 42L;

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingGuestRepository bookingGuestRepository;
    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private BookingSourceRepository bookingSourceRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
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

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                bookingRoomRepository,
                bookingGuestRepository,
                bookingSourceRepository,
                customerProfileRepository,
                roomRepository,
                bookingCalculatorService,
                cancellationPolicyService,
                emailService,
                FIXED_CLOCK
        );
        lenient().doAnswer(invocation -> {
            BookingRoom bookingRoom = invocation.getArgument(0);
            CancellationPolicy policy = invocation.getArgument(1);
            bookingRoom.setCancellationPolicy(policy);
            bookingRoom.setCancellationPolicySnapshot("{\"code\":\"" + policy.getCode() + "\"}");
            return null;
        }).when(cancellationPolicyService).applyPolicySnapshot(any(BookingRoom.class), any(CancellationPolicy.class));
    }

    @Test
    void createBookingBuildsPendingBookingWithSnapshots() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Room room = createRoom(10L, "A101", "DLX", "Deluxe");
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 3);
        BookingPriceCalculationResponse priceCalculation = new BookingPriceCalculationResponse(
                10L, 5L, checkIn, checkOut, 2,
                2, 0,
                List.of(
                        new DailyRateResponse(checkIn, money("1000000.00")),
                        new DailyRateResponse(checkIn.plusDays(1), money("1000000.00"))
                ),
                money("2000000.00"), money("10.00"), money("200000.00"), money("2200000.00"), "VND"
        );

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRoomRepository.existsOverlappingBooking(eq(10L), any(), eq(checkIn), eq(checkOut)))
                .thenReturn(false);
        when(bookingCalculatorService.calculatePrice(any(BookingPriceCalculationRequest.class)))
                .thenReturn(priceCalculation);
        when(roomRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, checkIn, checkOut, 2, 0))
        );

        BookingResponse response = bookingService.createBooking(request, USER_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.bookingCode()).matches("^BK-\\d{4}-\\d{6}$");
        assertThat(response.contactName()).isEqualTo("Nguyen Van A");
        assertThat(response.contactEmail()).isEqualTo("guest@example.com");
        assertThat(response.contactPhone()).isEqualTo("0900000000");
        assertThat(response.adults()).isEqualTo(1);
        assertThat(response.children()).isEqualTo(0);
        assertThat(response.roomsTotal()).isEqualByComparingTo("2000000.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("200000.00");
        assertThat(response.roomTaxPercentSnapshot()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("2200000.00");
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.holdExpiresAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(15));
        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomNumber()).isEqualTo("A101");
        assertThat(response.rooms().getFirst().nights()).hasSize(2);
        assertThat(response.rooms().getFirst().nights().getFirst().price()).isEqualByComparingTo("1000000.00");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());
        Booking savedBooking = captor.getValue();
        assertThat(savedBooking.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedBooking.getStatusHistory()).hasSize(1);
        var historyEntry = savedBooking.getStatusHistory().iterator().next();
        assertThat(historyEntry.getFromStatus()).isNull();
        assertThat(historyEntry.getToStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(historyEntry.getActorType()).isEqualTo(ActorType.USER);
        assertThat(historyEntry.getChangedBy()).isEqualTo(USER_ID);
        assertThat(historyEntry.getSource()).isEqualTo(StatusChangeSource.MANUAL);

        assertThat(response.rooms().getFirst().cancellationPolicyCode()).isEqualTo("FLEXIBLE");
        verify(cancellationPolicyService).applyPolicySnapshot(any(BookingRoom.class), eq(room.getRoomType().getCancellationPolicy()));
    }

    @Test
    void createBookingAggregatesTotalsAcrossMultipleRooms() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Room roomA = createRoom(10L, "A101", "DLX", "Deluxe");
        Room roomB = createRoom(20L, "B202", "STD", "Standard");
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 2);

        BookingPriceCalculationResponse calcA = new BookingPriceCalculationResponse(
                10L, 5L, checkIn, checkOut, 1, 2, 0,
                List.of(new DailyRateResponse(checkIn, money("1000000.00"))),
                money("1000000.00"), money("10.00"), money("100000.00"), money("1100000.00"), "VND"
        );
        BookingPriceCalculationResponse calcB = new BookingPriceCalculationResponse(
                20L, 6L, checkIn, checkOut, 1, 1, 1,
                List.of(new DailyRateResponse(checkIn, money("500000.00"))),
                money("500000.00"), money("10.00"), money("50000.00"), money("550000.00"), "VND"
        );

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRoomRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingCalculatorService.calculatePrice(argThat(r -> r != null && r.roomId().equals(10L))))
                .thenReturn(calcA);
        when(bookingCalculatorService.calculatePrice(argThat(r -> r != null && r.roomId().equals(20L))))
                .thenReturn(calcB);
        when(roomRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(roomA));
        when(roomRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(roomB));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(
                        new BookingRoomCreateItem(10L, checkIn, checkOut, 2, 0),
                        new BookingRoomCreateItem(20L, checkIn, checkOut, 1, 1)
                )
        );

        BookingResponse response = bookingService.createBooking(request, USER_ID);

        assertThat(response.adults()).isEqualTo(2);
        assertThat(response.children()).isEqualTo(0);
        assertThat(response.roomsTotal()).isEqualByComparingTo("1500000.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("150000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("1650000.00");
        assertThat(response.rooms()).hasSize(2);
    }

    @Test
    void createBookingAssignsDifferentRoomsWhenSameRoomTypeIsRequestedTwice() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Room roomA = createRoom(10L, "A101", "DLX", "Deluxe");
        Room roomB = createRoom(20L, "A102", "DLX", "Deluxe");
        roomB.setRoomType(roomA.getRoomType());
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 3);
        BookingPriceCalculationResponse priceCalculation = new BookingPriceCalculationResponse(
                10L, roomA.getRoomType().getId(), checkIn, checkOut, 2,
                1, 0,
                List.of(
                        new DailyRateResponse(checkIn, money("1000000.00")),
                        new DailyRateResponse(checkIn.plusDays(1), money("1000000.00"))
                ),
                money("2000000.00"), money("10.00"), money("200000.00"),
                money("2200000.00"), "VND"
        );

        bookingService = new BookingService(
                bookingRepository,
                bookingGuestRepository,
                bookingRoomRepository,
                bookingSourceRepository,
                null,
                null,
                customerProfileRepository,
                null,
                null,
                null,
                roomRepository,
                bookingCalculatorService,
                cancellationPolicyService,
                bookingOptionResolverService,
                emailService,
                FIXED_CLOCK,
                null,
                null,
                null
        );

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingOptionResolverService.resolve("DLX", BookingPaymentOption.ONLINE, "FLEXIBLE"))
                .thenReturn(new BookingOptionSelection(
                        roomA.getRoomType(),
                        BookingPaymentOption.ONLINE,
                        roomA.getRoomType().getCancellationPolicy(),
                        BigDecimal.ZERO
                ));
        when(roomRepository.findAvailableRoomsByTypeForUpdate(
                eq("DLX"), eq(checkIn), eq(checkOut), eq(RoomOperationalStatus.ACTIVE), any()
        )).thenReturn(List.of(roomA, roomB));
        when(bookingCalculatorService.calculatePrice(any(BookingPriceCalculationRequest.class)))
                .thenReturn(priceCalculation);
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(
                        new BookingRoomCreateItem(
                                "DLX", BookingPaymentOption.ONLINE, "FLEXIBLE",
                                checkIn, checkOut, 1, 0, "Guest A"
                        ),
                        new BookingRoomCreateItem(
                                "DLX", BookingPaymentOption.ONLINE, "FLEXIBLE",
                                checkIn, checkOut, 1, 0, "Guest B"
                        )
                )
        );

        BookingResponse response = bookingService.createBooking(request, USER_ID);

        assertThat(response.rooms()).extracting(room -> room.roomNumber())
                .containsExactlyInAnyOrder("A101", "A102");
    }

    @Test
    void createBookingRejectsWhenCustomerProfileMissing() {
        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1, 0))
        );

        assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(bookingSourceRepository, never()).findByCodeIgnoreCaseAndIsActiveTrue(anyString());
    }

    @Test
    void createBookingRejectsOverlappingRoom() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 2);

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRoomRepository.existsOverlappingBooking(eq(10L), any(), eq(checkIn), eq(checkOut)))
                .thenReturn(true);

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, checkIn, checkOut, 1, 0))
        );

        assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID))
                .isInstanceOf(BookingRoomConflictException.class);
        verify(bookingCalculatorService, never()).calculatePrice(any());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBookingRejectsInvalidDateRange() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        LocalDate checkIn = LocalDate.of(2026, 9, 5);
        LocalDate checkOut = LocalDate.of(2026, 9, 1);

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, checkIn, checkOut, 1, 0))
        );

        assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(bookingCalculatorService, never()).calculatePrice(any());
    }

    @Test
    void createBookingWrapsDatabaseConflictAsBookingRoomConflict() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Room room = createRoom(10L, "A101", "DLX", "Deluxe");
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 2);
        BookingPriceCalculationResponse priceCalculation = new BookingPriceCalculationResponse(
                10L, 5L, checkIn, checkOut, 1, 1, 0,
                List.of(new DailyRateResponse(checkIn, money("1000000.00"))),
                money("1000000.00"), money("10.00"), money("100000.00"), money("1100000.00"), "VND"
        );

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRoomRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingCalculatorService.calculatePrice(any())).thenReturn(priceCalculation);
        when(roomRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("trigger rejected"));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, checkIn, checkOut, 1, 0))
        );

        assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID))
                .isInstanceOf(BookingRoomConflictException.class);
    }

    @Test
    void createBookingRetriesBookingCodeGenerationOnCollision() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Room room = createRoom(10L, "A101", "DLX", "Deluxe");
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 2);
        BookingPriceCalculationResponse priceCalculation = new BookingPriceCalculationResponse(
                10L, 5L, checkIn, checkOut, 1, 1, 0,
                List.of(new DailyRateResponse(checkIn, money("1000000.00"))),
                money("1000000.00"), money("10.00"), money("100000.00"), money("1100000.00"), "VND"
        );

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRoomRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingCalculatorService.calculatePrice(any())).thenReturn(priceCalculation);
        when(roomRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(true, true, false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(10L, checkIn, checkOut, 1, 0))
        );

        BookingResponse response = bookingService.createBooking(request, USER_ID);

        assertThat(response.bookingCode()).matches("^BK-\\d{4}-\\d{6}$");
    }

    @Test
    void createBookingFailsWhenBookingCodeAlwaysCollides() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();

        when(customerProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(customerProfile));
        when(bookingSourceRepository.findByCodeIgnoreCaseAndIsActiveTrue("WEBSITE"))
                .thenReturn(Optional.of(source));
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(true);

        BookingCreateRequest request = new BookingCreateRequest(
                null, null, null, null,
                List.of(new BookingRoomCreateItem(
                        10L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1, 0
                ))
        );

        assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void removePendingBookingRoomDeletesRoomAndRecalculatesTotals() {
        Booking booking = createPendingBookingWithRooms();
        BookingRoom roomToRemove = booking.getBookingRooms().stream()
                .filter(room -> room.getId().equals(11L))
                .findFirst()
                .orElseThrow();

        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id("booking-public-id", USER_ID))
                .thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);

        BookingResponse response = bookingService.removePendingBookingRoom(
                "booking-public-id",
                11L,
                USER_ID
        );

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.roomsTotal()).isEqualByComparingTo("500000.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("50000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("550000.00");
        verify(bookingGuestRepository).deleteAllByBookingRoomId(11L);
        verify(bookingRoomRepository).delete(roomToRemove);
        verify(bookingRepository).saveAndFlush(booking);
    }

    @Test
    void deletePendingBookingHardDeletesUnpaidPendingBooking() {
        Booking booking = createPendingBookingWithRooms();
        when(bookingRepository.findByPublicIdAndCustomerProfile_User_Id("booking-public-id", USER_ID))
                .thenReturn(Optional.of(booking));
        bookingService.deletePendingBooking("booking-public-id", USER_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookingRepository).delete(booking);
        verify(emailService, never()).sendBookingCancelledEmail(any());
    }

    @Test
    void getMyBookingsKeepsHistoricalCancelledBookingsVisible() {
        Booking visibleBooking = createPendingBookingWithRooms();
        Booking removedBooking = createPendingBookingWithRooms();
        removedBooking.setPublicId("removed-booking-public-id");
        removedBooking.setStatus(BookingStatus.CANCELLED);
        removedBooking.setCancellationReason("Customer removed pending booking before payment");
        when(bookingRepository.findAllByCustomerProfile_User_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(removedBooking, visibleBooking));

        List<BookingResponse> responses = bookingService.getMyBookings(USER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(BookingResponse::publicId)
                .containsExactly("removed-booking-public-id", "booking-public-id");
    }

    @Test
    void getMyBookingDetailReturnsOwnedBookingWithOrderedTimeline() {
        Booking booking = createPendingBookingWithRooms();
        BookingStatusHistory createdHistory = BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(null)
                .toStatus(BookingStatus.PENDING)
                .actorType(ActorType.USER)
                .changedBy(USER_ID)
                .source(StatusChangeSource.MANUAL)
                .build();
        createdHistory.setCreatedAt(OffsetDateTime.parse("2026-08-20T08:00:00Z"));
        BookingStatusHistory confirmedHistory = BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(BookingStatus.PENDING)
                .toStatus(BookingStatus.CONFIRMED)
                .actorType(ActorType.SYSTEM)
                .source(StatusChangeSource.PAYMENT_CALLBACK)
                .build();
        confirmedHistory.setCreatedAt(OffsetDateTime.parse("2026-08-20T08:05:00Z"));
        booking.getStatusHistory().add(confirmedHistory);
        booking.getStatusHistory().add(createdHistory);

        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id("booking-public-id", USER_ID))
                .thenReturn(Optional.of(booking));

        BookingDetailResponse response = bookingService.getMyBookingDetail("booking-public-id", USER_ID);

        assertThat(response.booking().publicId()).isEqualTo("booking-public-id");
        assertThat(response.booking().rooms()).hasSize(2);
        assertThat(response.statusHistory())
                .extracting(history -> history.toStatus())
                .containsExactly(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }

    @Test
    void getMyBookingDetailHidesBookingsNotOwnedByCurrentCustomer() {
        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id("other-booking", USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getMyBookingDetail("other-booking", USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyBookingDetailReturnsHistoricalCancelledBooking() {
        Booking removedBooking = createPendingBookingWithRooms();
        removedBooking.setStatus(BookingStatus.CANCELLED);
        removedBooking.setCancellationReason("Customer removed pending booking before payment");
        when(bookingRepository.findOneByPublicIdAndCustomerProfile_User_Id("booking-public-id", USER_ID))
                .thenReturn(Optional.of(removedBooking));

        BookingDetailResponse response = bookingService.getMyBookingDetail("booking-public-id", USER_ID);

        assertThat(response.booking().status()).isEqualTo(BookingStatus.CANCELLED);
    }

    private CustomerProfile createCustomerProfile() {
        User user = User.builder()
                .publicId("user-public-id")
                .email("guest@example.com")
                .fullName("Nguyen Van A")
                .phone("0900000000")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        return CustomerProfile.builder().user(user).build();
    }

    private BookingSource createSource() {
        return BookingSource.builder()
                .code("WEBSITE")
                .name("Website")
                .isExternal(false)
                .requiresAccount(true)
                .commissionPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private Room createRoom(Long roomId, String roomNumber, String roomTypeCode, String roomTypeName) {
        RoomType roomType = RoomType.builder()
                .code(roomTypeCode)
                .name(roomTypeName)
                .slug(roomTypeCode.toLowerCase())
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(1)
                .basePrice(money("1000000.00"))
                .isActive(true)
                .cancellationPolicy(createPolicy("FLEXIBLE", "Flexible"))
                .build();
        roomType.setId(roomId + 100);
        Room room = Room.builder()
                .roomType(roomType)
                .roomNumber(roomNumber)
                .isActive(true)
                .build();
        room.setId(roomId);
        return room;
    }

    private Booking createPendingBookingWithRooms() {
        CustomerProfile customerProfile = createCustomerProfile();
        BookingSource source = createSource();
        Booking booking = Booking.builder()
                .publicId("booking-public-id")
                .bookingCode("BK-2026-000001")
                .customerProfile(customerProfile)
                .source(source)
                .sourceCommissionPercentSnapshot(BigDecimal.ZERO)
                .status(BookingStatus.PENDING)
                .contactName("Nguyen Van A")
                .contactEmail("guest@example.com")
                .contactPhone("0900000000")
                .adults(3)
                .children(0)
                .roomsTotal(money("1500000.00"))
                .taxTotal(money("150000.00"))
                .roomTaxPercentSnapshot(money("10.00"))
                .totalAmount(money("1650000.00"))
                .currency("VND")
                .holdExpiresAt(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(15))
                .build();

        BookingRoom roomA = createBookingRoom(11L, booking, createRoom(10L, "A101", "DLX", "Deluxe"), "1000000.00", 2);
        BookingRoom roomB = createBookingRoom(12L, booking, createRoom(20L, "B202", "STD", "Standard"), "500000.00", 1);
        booking.getBookingRooms().add(roomA);
        booking.getBookingRooms().add(roomB);
        booking.getBookingGuests().add(BookingGuest.builder()
                .booking(booking)
                .bookingRoom(roomA)
                .fullName("Nguyen Van A")
                .build());
        booking.getBookingGuests().add(BookingGuest.builder()
                .booking(booking)
                .bookingRoom(roomB)
                .fullName("Tran Van B")
                .build());
        return booking;
    }

    private BookingRoom createBookingRoom(
            Long id,
            Booking booking,
            Room room,
            String roomSubtotal,
            int guestCount
    ) {
        BookingRoom bookingRoom = BookingRoom.builder()
                .booking(booking)
                .room(room)
                .roomType(room.getRoomType())
                .roomTypeCodeSnapshot(room.getRoomType().getCode())
                .roomTypeNameSnapshot(room.getRoomType().getName())
                .checkInDate(LocalDate.of(2026, 9, 1))
                .checkOutDate(LocalDate.of(2026, 9, 2))
                .roomSubtotal(money(roomSubtotal))
                .cancellationPolicy(room.getRoomType().getCancellationPolicy())
                .status(com.example.hotelmanagement.entity.enums.BookingRoomStatus.RESERVED)
                .guestCount(guestCount)
                .build();
        bookingRoom.setId(id);
        return bookingRoom;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private CancellationPolicy createPolicy(String code, String name) {
        return CancellationPolicy.builder()
                .code(code)
                .name(name)
                .description(name + " policy")
                .noShowChargePercent(new BigDecimal("100.00"))
                .isDefault(true)
                .isActive(true)
                .build();
    }
}
