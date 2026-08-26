package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.PaymentProperties;
import com.example.hotelmanagement.dto.payment.PaymentCreateRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final Long USER_ID = 42L;
    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String IDEMPOTENCY_KEY = "payment-attempt-001";

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayRegistry paymentGatewayRegistry;
    @Mock
    private PaymentGatewayService paymentGatewayService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        PaymentProperties paymentProperties = new PaymentProperties();
        paymentProperties.setDefaultProvider("SEPAY");
        org.mockito.Mockito.lenient()
                .when(paymentGatewayRegistry.getGateway(anyString(), any(PaymentMethod.class)))
                .thenReturn(paymentGatewayService);
        org.mockito.Mockito.lenient().when(paymentGatewayService.getProviderCode()).thenReturn("SEPAY");
        org.mockito.Mockito.lenient().when(paymentGatewayService.createCheckout(any(Payment.class)))
                .thenReturn(new PaymentGatewayCheckout(
                        "SEPAY",
                        "https://pay-sandbox.sepay.vn/v1/checkout/init",
                        null,
                        null,
                        List.of(new com.example.hotelmanagement.dto.payment.PaymentGatewayFormField(
                                "merchant",
                                "sandbox-merchant"
                        ))
                ));
        paymentService = new PaymentService(
                bookingRepository,
                paymentRepository,
                paymentGatewayRegistry,
                paymentProperties,
                FIXED_CLOCK
        );
    }

    @Test
    void createPaymentCreatesPendingPaymentForOutstandingBookingBalance() {
        Booking booking = createPayableBooking();
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.INTERNET_BANKING),
                IDEMPOTENCY_KEY,
                USER_ID
        );

        assertThat(response.paymentCode()).matches("^PAY-2026-[0-9A-F]{20}$");
        assertThat(response.bookingPublicId()).isEqualTo(BOOKING_PUBLIC_ID);
        assertThat(response.method()).isEqualTo(PaymentMethod.INTERNET_BANKING);
        assertThat(response.amount()).isEqualByComparingTo("1250000.00");
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.provider()).isEqualTo("SEPAY");
        assertThat(response.paymentUrl()).isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
        assertThat(response.checkoutFields()).hasSize(1);
        assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(10));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getBooking()).isSameAs(booking);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo("1250000.00");
        assertThat(savedPayment.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void createPaymentReturnsExistingPaymentForMatchingIdempotencyKey() {
        Booking booking = createPayableBooking();
        Payment existingPayment = Payment.builder()
                .paymentCode("PAY-2026-0123456789ABCDEF0123")
                .booking(booking)
                .method(PaymentMethod.CARD)
                .amount(money("1250000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .provider("SEPAY")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .expiresAt(booking.getHoldExpiresAt())
                .build();
        existingPayment.setCreatedAt(OffsetDateTime.now(FIXED_CLOCK));

        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.CARD),
                IDEMPOTENCY_KEY,
                USER_ID
        );

        assertThat(response.paymentCode()).isEqualTo(existingPayment.getPaymentCode());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPaymentRejectsBookingOwnedByAnotherUser() {
        Booking booking = createPayableBooking();
        booking.getCustomerProfile().getUser().setId(USER_ID + 1);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.E_WALLET),
                IDEMPOTENCY_KEY,
                USER_ID
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot access payments");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPaymentRejectsExpiredBookingHold() {
        Booking booking = createPayableBooking();
        booking.setHoldExpiresAt(OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.BANK_TRANSFER),
                IDEMPOTENCY_KEY,
                USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("hold has expired");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPaymentRejectsSecondActivePaymentForBooking() {
        Booking booking = createPayableBooking();
        Payment pendingPayment = Payment.builder()
                .paymentCode("PAY-2026-0123456789ABCDEF0123")
                .booking(booking)
                .method(PaymentMethod.INTERNET_BANKING)
                .amount(money("1250000.00"))
                .status(PaymentStatus.PENDING)
                .build();
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.INTERNET_BANKING),
                IDEMPOTENCY_KEY,
                USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already pending");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPaymentSupportsMockElectronicWalletCheckout() {
        Booking booking = createPayableBooking();
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayRegistry.getGateway("MOCK_WALLET", PaymentMethod.E_WALLET))
                .thenReturn(paymentGatewayService);
        when(paymentGatewayService.getProviderCode()).thenReturn("MOCK_WALLET");
        when(paymentGatewayService.createCheckout(any(Payment.class)))
                .thenReturn(new PaymentGatewayCheckout(
                        "MOCK_WALLET",
                        "http://localhost:3000/payment/mock-wallet/PAY-2026-001",
                        null,
                        "MOCK_WALLET|PAY-2026-001|375000.00|VND",
                        List.of()
                ));

        PaymentResponse response = paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.E_WALLET),
                IDEMPOTENCY_KEY,
                USER_ID
        );

        assertThat(response.provider()).isEqualTo("MOCK_WALLET");
        assertThat(response.paymentUrl()).contains("/payment/mock-wallet/");
        assertThat(response.qrCodeValue()).startsWith("MOCK_WALLET|");
    }

    @Test
    void createPaymentExpiresOldAttemptAndAllowsRetryWithinBookingHold() {
        Booking booking = createPayableBooking();
        Payment expiredAttempt = paymentForBooking(booking, PaymentStatus.PENDING);
        expiredAttempt.setExpiresAt(OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1));
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBooking_IdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(expiredAttempt));
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.createPayment(
                BOOKING_PUBLIC_ID,
                new PaymentCreateRequest(PaymentMethod.INTERNET_BANKING),
                IDEMPOTENCY_KEY,
                USER_ID
        );

        assertThat(expiredAttempt.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(expiredAttempt.getFailureCode()).isEqualTo("PAYMENT_LINK_EXPIRED");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void getPaymentLazilyExpiresLinkAndMarksItRetryable() {
        Booking booking = createPayableBooking();
        Payment payment = paymentForBooking(booking, PaymentStatus.PENDING);
        payment.setExpiresAt(OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1));
        when(paymentRepository.findForUpdateByPaymentCode(payment.getPaymentCode()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        var response = paymentService.getPayment(
                BOOKING_PUBLIC_ID,
                payment.getPaymentCode(),
                USER_ID
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(response.failureCode()).isEqualTo("PAYMENT_LINK_EXPIRED");
        assertThat(response.retryable()).isTrue();
    }

    @Test
    void cancelPaymentMarksActivePaymentCancelledAndRetryable() {
        Booking booking = createPayableBooking();
        Payment payment = paymentForBooking(booking, PaymentStatus.PROCESSING);
        when(paymentRepository.findForUpdateByPaymentCode(payment.getPaymentCode()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        var response = paymentService.cancelPayment(
                BOOKING_PUBLIC_ID,
                payment.getPaymentCode(),
                USER_ID
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.failureCode()).isEqualTo("CUSTOMER_CANCELLED");
        assertThat(response.retryable()).isTrue();
    }

    @Test
    void getFailedPaymentMarksItRetryableWithinBookingHold() {
        Booking booking = createPayableBooking();
        Payment payment = paymentForBooking(booking, PaymentStatus.FAILED);
        payment.setFailureCode("DECLINED");
        payment.setFailureMessage("Payment was declined");
        when(paymentRepository.findForUpdateByPaymentCode(payment.getPaymentCode()))
                .thenReturn(Optional.of(payment));

        var response = paymentService.getPayment(
                BOOKING_PUBLIC_ID,
                payment.getPaymentCode(),
                USER_ID
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.retryable()).isTrue();
    }

    private Booking createPayableBooking() {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000001")
                .customerProfile(createCustomerProfile())
                .status(BookingStatus.PENDING)
                .totalAmount(money("1250000.00"))
                .paidAmount(BigDecimal.ZERO)
                .currency("VND")
                .holdExpiresAt(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(15))
                .createdBy(USER_ID)
                .build();
        booking.setId(10L);
        return booking;
    }

    private CustomerProfile createCustomerProfile() {
        User user = User.builder()
                .publicId("22222222-2222-2222-2222-222222222222")
                .email("customer@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(USER_ID);
        return CustomerProfile.builder().user(user).build();
    }

    private Payment paymentForBooking(Booking booking, PaymentStatus status) {
        Payment payment = Payment.builder()
                .paymentCode("PAY-2026-0123456789ABCDEF0123")
                .booking(booking)
                .method(PaymentMethod.INTERNET_BANKING)
                .provider("SEPAY")
                .amount(money("1250000.00"))
                .currency("VND")
                .status(status)
                .expiresAt(OffsetDateTime.now(FIXED_CLOCK).plusMinutes(10))
                .build();
        payment.setId(20L);
        payment.setCreatedAt(OffsetDateTime.now(FIXED_CLOCK));
        payment.setUpdatedAt(OffsetDateTime.now(FIXED_CLOCK));
        return payment;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
