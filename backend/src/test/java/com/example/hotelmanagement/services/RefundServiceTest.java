package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.refund.RefundCompleteRequest;
import com.example.hotelmanagement.dto.refund.RefundResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundReason;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RefundRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final String BOOKING_PUBLIC_ID = "booking-public-id";
    private static final Long CUSTOMER_USER_ID = 1L;
    private static final Long STAFF_USER_ID = 99L;
    private static final Long BOOKING_ID = 10L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T09:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String FLEXIBLE_POLICY_JSON = """
            {"code":"FLEXIBLE","name":"Flexible","rules":[
                {"min_hours_before":72,"refund_percent":100.00},
                {"min_hours_before":30,"refund_percent":50.00},
                {"min_hours_before":0,"refund_percent":0.00}
            ]}
            """;

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentLedgerService paymentLedgerService;
    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                refundRepository,
                bookingRepository,
                paymentRepository,
                paymentLedgerService,
                hotelSettingsRepository,
                new ObjectMapper(),
                FIXED_CLOCK
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestRefundByOwnerCalculatesNetRefundFromSnapshot() {
        stubHotelSettings();
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), new BigDecimal("100000.00"), new BigDecimal("10.00"),
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(false);
        Payment payment = receivedPayment();
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID);

        // 79h before scheduled 14:00 check-in -> 100% rule; gross = 1,100,000; minus 10% commission -> 990,000
        assertThat(response.amount()).isEqualByComparingTo("990000.00");
        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(response.reason()).isEqualTo(RefundReason.CUSTOMER_CANCEL);
        assertThat(response.policyApplied()).contains("\"net_refund\":990000.00");
    }

    @Test
    void requestRefundByStaffUsesHotelCancelReason() {
        stubHotelSettings();
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(receivedPayment()));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs("refund:approve");

        RefundResponse response = refundService.requestRefund(BOOKING_PUBLIC_ID, STAFF_USER_ID);

        assertThat(response.reason()).isEqualTo(RefundReason.HOTEL_CANCEL);
    }

    @Test
    void requestRefundRejectsNonOwnerWithoutRefundApprove() {
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        authenticateAs("booking:read_own");

        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_PUBLIC_ID, STAFF_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsBookingNotCancelled() {
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        booking.setStatus(BookingStatus.CHECKED_OUT);
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(paymentRepository, never()).findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void requestRefundRejectsDuplicateActiveRefund() {
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(DuplicateResourceException.class);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsWhenNoReceivedPayment() {
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsWhenNoRefundIsDue() {
        stubHotelSettings();
        // Cancelled only 5 hours before check-in -> matches the 0-hour / 0% rule.
        Booking booking = cancelledBooking(
                new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                FLEXIBLE_POLICY_JSON, OffsetDateTime.parse("2026-08-23T02:00:00Z"),
                LocalDate.of(2026, 8, 23), true
        );
        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(receivedPayment()));

        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("No refund is due");
        verify(refundRepository, never()).save(any());
    }

    @Test
    void requestRefundSumsMultipleRoomsAndUsesEarliestRoomForServices() {
        stubHotelSettings();
        User user = createUser(CUSTOMER_USER_ID, "customer@example.com");
        CustomerProfile customerProfile = CustomerProfile.builder().user(user).build();
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .status(BookingStatus.CANCELLED)
                .cancelledAt(OffsetDateTime.parse("2026-08-20T00:00:00Z"))
                .customerProfile(customerProfile)
                .servicesTotal(new BigDecimal("100000.00"))
                .sourceCommissionPercentSnapshot(BigDecimal.ZERO)
                .build();
        booking.setId(BOOKING_ID);
        // Check-in 2026-08-23, cancelled 2026-08-20T00:00Z -> 79h before -> matches the 72h/100% rule.
        booking.getBookingRooms().add(bookingRoom(
                booking, LocalDate.of(2026, 8, 23), new BigDecimal("1000000.00"), FLEXIBLE_POLICY_JSON
        ));
        // Check-in 2026-08-21 (earliest room) -> 31h before -> matches the 30h/50% rule; this room's
        // match is also used for the services_total portion since it has the earliest check-in.
        booking.getBookingRooms().add(bookingRoom(
                booking, LocalDate.of(2026, 8, 21), new BigDecimal("500000.00"), FLEXIBLE_POLICY_JSON
        ));

        when(bookingRepository.findByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(refundRepository.existsByBooking_IdAndStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(receivedPayment()));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.requestRefund(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID);

        // room(08-23): 1,000,000 * 100% = 1,000,000 ; room(08-21): 500,000 * 50% = 250,000
        // services (earliest room's 50% rule): 100,000 * 50% = 50,000
        // gross = 1,300,000, no commission -> net = 1,300,000
        assertThat(response.amount()).isEqualByComparingTo("1300000.00");
    }

    @Test
    void approveTransitionsPendingToProcessing() {
        Refund refund = refund(RefundStatus.PENDING);
        when(refundRepository.findForUpdateById(500L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(refund)).thenReturn(refund);

        RefundResponse response = refundService.approve(BOOKING_PUBLIC_ID, 500L, STAFF_USER_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.getApprovedBy()).isEqualTo(STAFF_USER_ID);
    }

    @Test
    void approveRejectsNonPendingRefund() {
        Refund refund = refund(RefundStatus.PROCESSING);
        when(refundRepository.findForUpdateById(500L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.approve(BOOKING_PUBLIC_ID, 500L, STAFF_USER_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void approveThrowsWhenRefundBelongsToDifferentBooking() {
        Refund refund = refund(RefundStatus.PENDING);
        when(refundRepository.findForUpdateById(500L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.approve("some-other-booking", 500L, STAFF_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completeTransitionsProcessingToCompletedAndSynchronizesLedger() {
        Refund refund = refund(RefundStatus.PROCESSING);
        when(refundRepository.findForUpdateById(500L)).thenReturn(Optional.of(refund));
        when(refundRepository.saveAndFlush(refund)).thenReturn(refund);

        RefundResponse response = refundService.complete(
                BOOKING_PUBLIC_ID, 500L, new RefundCompleteRequest("PROVIDER-REF-1")
        );

        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getProviderRefundId()).isEqualTo("PROVIDER-REF-1");
        assertThat(refund.getProcessedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(paymentLedgerService).synchronizeCompletedRefund(captor.capture());
        assertThat(captor.getValue()).isSameAs(refund);
    }

    @Test
    void completeRejectsNonProcessingRefund() {
        Refund refund = refund(RefundStatus.PENDING);
        when(refundRepository.findForUpdateById(500L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.complete(BOOKING_PUBLIC_ID, 500L, null))
                .isInstanceOf(BusinessValidationException.class);
        verify(paymentLedgerService, never()).synchronizeCompletedRefund(any());
    }

    private void stubHotelSettings() {
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY)).thenReturn("Asia/Ho_Chi_Minh");
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CHECK_IN_TIME_KEY)).thenReturn("14:00");
    }

    private void authenticateAs(String... authorities) {
        List<GrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        Authentication authentication = new TestingAuthenticationToken("staff@example.com", "N/A", grantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Booking cancelledBooking(
            BigDecimal roomSubtotal,
            BigDecimal servicesTotal,
            BigDecimal commissionPercent,
            String policySnapshotJson,
            OffsetDateTime cancelledAt,
            LocalDate checkInDate,
            boolean withCustomer
    ) {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .status(BookingStatus.CANCELLED)
                .cancelledAt(cancelledAt)
                .servicesTotal(servicesTotal)
                .sourceCommissionPercentSnapshot(commissionPercent)
                .build();
        booking.setId(BOOKING_ID);
        if (withCustomer) {
            User user = createUser(CUSTOMER_USER_ID, "customer@example.com");
            booking.setCustomerProfile(CustomerProfile.builder().user(user).build());
        }
        booking.getBookingRooms().add(bookingRoom(booking, checkInDate, roomSubtotal, policySnapshotJson));
        return booking;
    }

    private BookingRoom bookingRoom(Booking booking, LocalDate checkInDate, BigDecimal roomSubtotal, String policySnapshotJson) {
        BookingRoom room = BookingRoom.builder()
                .booking(booking)
                .roomTypeCodeSnapshot("DLX")
                .roomTypeNameSnapshot("Deluxe")
                .checkInDate(checkInDate)
                .checkOutDate(checkInDate.plusDays(2))
                .roomSubtotal(roomSubtotal)
                .cancellationPolicySnapshot(policySnapshotJson)
                .build();
        room.setId((long) (500 + checkInDate.getDayOfMonth()));
        return room;
    }

    private User createUser(Long id, String email) {
        User user = User.builder()
                .publicId("user-" + id)
                .email(email)
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(id);
        return user;
    }

    private Payment receivedPayment() {
        Payment payment = Payment.builder()
                .paymentCode("PAY-0001")
                .amount(new BigDecimal("2000000.00"))
                .status(PaymentStatus.SUCCEEDED)
                .build();
        payment.setId(900L);
        return payment;
    }

    private Refund refund(RefundStatus status) {
        Booking booking = Booking.builder().publicId(BOOKING_PUBLIC_ID).build();
        booking.setId(BOOKING_ID);
        Refund refund = Refund.builder()
                .payment(receivedPayment())
                .booking(booking)
                .amount(new BigDecimal("500000.00"))
                .reason(RefundReason.CUSTOMER_CANCEL)
                .status(status)
                .requestedBy(CUSTOMER_USER_ID)
                .build();
        refund.setId(500L);
        return refund;
    }
}
