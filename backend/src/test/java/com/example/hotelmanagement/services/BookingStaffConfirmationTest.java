package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingConfirmResponse;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingStaffConfirmationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final Long STAFF_USER_ID = 42L;

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private FolioChargeRepository folioChargeRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BookingGuestRepository bookingGuestRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private BookingGuestService bookingGuestService;
    @Mock
    private BookingStateMachineService bookingStateMachineService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentManagementService paymentManagementService;

    @Test
    void confirmingUnpaidStaffBookingCreatesAndVerifiesCashPayment() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        BookingStaffService service = new BookingStaffService(
                bookingRepository,
                bookingRoomRepository,
                roomRepository,
                folioChargeRepository,
                paymentRepository,
                invoiceRepository,
                bookingGuestRepository,
                staffProfileRepository,
                bookingGuestService,
                bookingStateMachineService,
                paymentService,
                paymentManagementService,
                clock
        );
        Booking pending = staffBooking(BookingStatus.PENDING, BookingPaymentStatus.UNPAID);
        Booking confirmed = staffBooking(BookingStatus.CHECKED_IN, BookingPaymentStatus.PAID);
        PaymentResponse cashPayment = new PaymentResponse(
                "PAY-2026-CASH001",
                BOOKING_PUBLIC_ID,
                PaymentMethod.CASH,
                new BigDecimal("1250000.00"),
                "VND",
                PaymentStatus.PENDING,
                "MANUAL",
                null,
                null,
                null,
                List.of(),
                pending.getHoldExpiresAt(),
                pending.getCreatedAt()
        );
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(pending), Optional.of(confirmed), Optional.of(confirmed));
        when(paymentService.createStaffCashPayment(any(), any(), any())).thenReturn(cashPayment);

        BookingConfirmResponse response = service.confirmBooking(BOOKING_PUBLIC_ID, STAFF_USER_ID);

        verify(paymentService).createStaffCashPayment(
                eq(BOOKING_PUBLIC_ID),
                startsWith("staff-cash-confirmation:"),
                eq(STAFF_USER_ID)
        );
        verify(paymentManagementService).verifyCashPayment("PAY-2026-CASH001", null, STAFF_USER_ID);
        verify(bookingStateMachineService).checkIn(BOOKING_PUBLIC_ID, STAFF_USER_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    private Booking staffBooking(BookingStatus status, BookingPaymentStatus paymentStatus) {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000001")
                .source(BookingSource.builder().code("STAFF_MANUAL").build())
                .status(status)
                .paymentStatus(paymentStatus)
                .totalAmount(new BigDecimal("1250000.00"))
                .paidAmount(paymentStatus == BookingPaymentStatus.PAID
                        ? new BigDecimal("1250000.00") : BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .currency("VND")
                .holdExpiresAt(OffsetDateTime.parse("2026-08-27T00:15:00Z"))
                .build();
        booking.setId(10L);
        booking.setConfirmedAt(status == BookingStatus.CONFIRMED
                ? OffsetDateTime.parse("2026-08-27T00:01:00Z") : null);
        return booking;
    }
}
