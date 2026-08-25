package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.InvoicePaymentStatus;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundStatus;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.example.hotelmanagement.repositories.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLedgerServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;

    private PaymentLedgerService paymentLedgerService;

    @BeforeEach
    void setUp() {
        paymentLedgerService = new PaymentLedgerService(
                bookingRepository,
                invoiceRepository,
                paymentRepository,
                refundRepository
        );
    }

    @Test
    void synchronizeSuccessfulPaymentUpdatesBookingAndConfirmsWhenDepositIsMet() {
        Booking booking = booking();
        Payment payment = Payment.builder()
                .paymentCode("PAY-2026-001")
                .booking(booking)
                .amount(money("300000.00"))
                .status(PaymentStatus.SUCCEEDED)
                .verifiedAt(OffsetDateTime.now())
                .build();
        payment.setId(20L);

        when(bookingRepository.findForUpdateById(10L)).thenReturn(Optional.of(booking));
        when(paymentRepository.sumAmountsByBookingIdAndStatuses(eq(10L), any()))
                .thenReturn(money("300000.00"));
        when(refundRepository.sumAmountsByBookingIdAndStatus(10L, RefundStatus.COMPLETED))
                .thenReturn(BigDecimal.ZERO);

        PaymentLedgerResult result = paymentLedgerService.synchronizeSuccessfulPayment(payment);

        assertThat(booking.getPaidAmount()).isEqualByComparingTo("300000.00");
        assertThat(booking.getRefundedAmount()).isEqualByComparingTo("0.00");
        assertThat(booking.getPaymentStatus()).isEqualTo(BookingPaymentStatus.PARTIALLY_PAID);
        assertThat(result.bookingPublicId()).isEqualTo("booking-public-id");
        assertThat(result.shouldConfirmBooking()).isTrue();
    }

    @Test
    void synchronizeSuccessfulPaymentUpdatesItsLinkedInvoice() {
        Booking booking = booking();
        Invoice invoice = Invoice.builder()
                .booking(booking)
                .totalAmount(money("300000.00"))
                .paidAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build();
        invoice.setId(30L);
        Payment payment = Payment.builder()
                .paymentCode("PAY-2026-002")
                .booking(booking)
                .invoice(invoice)
                .amount(money("300000.00"))
                .status(PaymentStatus.SUCCEEDED)
                .verifiedAt(OffsetDateTime.now())
                .build();

        when(bookingRepository.findForUpdateById(10L)).thenReturn(Optional.of(booking));
        when(invoiceRepository.findForUpdateById(30L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.sumAmountsByBookingIdAndStatuses(eq(10L), any()))
                .thenReturn(money("300000.00"));
        when(refundRepository.sumAmountsByBookingIdAndStatus(10L, RefundStatus.COMPLETED))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountsByInvoiceIdAndStatuses(eq(30L), any()))
                .thenReturn(money("300000.00"));
        when(refundRepository.sumAmountsByInvoiceIdAndStatus(30L, RefundStatus.COMPLETED))
                .thenReturn(BigDecimal.ZERO);

        paymentLedgerService.synchronizeSuccessfulPayment(payment);

        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("300000.00");
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    private Booking booking() {
        Booking booking = Booking.builder()
                .publicId("booking-public-id")
                .bookingCode("BK-2026-000001")
                .status(BookingStatus.PENDING)
                .totalAmount(money("1000000.00"))
                .requiredDepositAmount(money("300000.00"))
                .paidAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build();
        booking.setId(10L);
        return booking;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
