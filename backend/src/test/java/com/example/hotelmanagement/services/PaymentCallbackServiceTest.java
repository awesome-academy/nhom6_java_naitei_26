package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.PaymentEvent;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.repositories.PaymentEventRepository;
import com.example.hotelmanagement.repositories.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String RAW_PAYLOAD = "{\"orderId\":\"PAY-2026-001\"}";

    @Mock
    private PaymentGatewayRegistry gatewayRegistry;
    @Mock
    private PaymentGatewayService gatewayService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private PaymentLedgerService paymentLedgerService;
    @Mock
    private BookingStateMachineService bookingStateMachineService;
    @Mock
    private EmailService emailService;

    private PaymentCallbackService callbackService;

    @BeforeEach
    void setUp() {
        callbackService = new PaymentCallbackService(
                gatewayRegistry,
                paymentRepository,
                paymentEventRepository,
                paymentLedgerService,
                bookingStateMachineService,
                emailService,
                new ObjectMapper(),
                FIXED_CLOCK
        );
        org.mockito.Mockito.lenient().when(gatewayRegistry.getGateway("sepay")).thenReturn(gatewayService);
        org.mockito.Mockito.lenient().when(paymentLedgerService.synchronizeSuccessfulPayment(any(Payment.class)))
                .thenReturn(new PaymentLedgerResult("booking-public-id", false));
    }

    @Test
    void handleCallbackMarksPaymentSucceededOnlyAfterVerification() {
        Payment payment = payment();
        PaymentGatewayCallback callback = successfulCallback(true);
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(callback);
        when(paymentEventRepository.existsByProviderAndProviderEventId("SEPAY", "event-1"))
                .thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderTxnId("4088878653")).thenReturn(Optional.empty());
        when(paymentLedgerService.synchronizeSuccessfulPayment(payment))
                .thenReturn(new PaymentLedgerResult("booking-public-id", true));

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTxnId()).isEqualTo("4088878653");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getVerifiedAt()).isNotNull();
        verify(paymentLedgerService).synchronizeSuccessfulPayment(payment);
        verify(emailService).sendPaymentSuccessEmail(payment);
        verify(bookingStateMachineService).confirm("booking-public-id");

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSignatureValid()).isTrue();
        assertThat(eventCaptor.getValue().getRawPayload()).isEqualTo(RAW_PAYLOAD);
    }

    @Test
    void handleCallbackDoesNotUpdatePaymentForInvalidSignature() {
        Payment payment = payment();
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(successfulCallback(false));
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getVerifiedAt()).isNull();
        verify(paymentRepository, never()).findByProviderTxnId(any());
        verify(paymentEventRepository).save(any(PaymentEvent.class));
    }

    @Test
    void handleCallbackDoesNotUpdatePaymentWhenAmountDoesNotMatch() {
        Payment payment = payment();
        PaymentGatewayCallback callback = new PaymentGatewayCallback(
                "SEPAY",
                "event-2",
                "PAY-2026-001",
                "4088878654",
                new BigDecimal("1200000"),
                0,
                "Successful.",
                "qr",
                true
        );
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(callback);
        when(paymentEventRepository.existsByProviderAndProviderEventId("SEPAY", "event-2"))
                .thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderTxnId()).isNull();
        assertThat(payment.getVerifiedAt()).isNull();
        verify(paymentRepository, never()).findByProviderTxnId(any());
        verify(paymentEventRepository).save(any(PaymentEvent.class));
    }

    @Test
    void handleCallbackIgnoresReplayEvent() {
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(successfulCallback(true));
        when(paymentEventRepository.existsByProviderAndProviderEventId("SEPAY", "event-1"))
                .thenReturn(true);

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        verify(paymentRepository, never()).findForUpdateByPaymentCode(any());
        verify(paymentEventRepository, never()).save(any());
    }

    @Test
    void handleCallbackMarksVerifiedFailureAsFailed() {
        Payment payment = payment();
        PaymentGatewayCallback callback = new PaymentGatewayCallback(
                "SEPAY",
                "event-failed",
                "PAY-2026-001",
                null,
                new BigDecimal("1250000"),
                1001,
                "Payment was declined",
                "CARD",
                true
        );
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(callback);
        when(paymentEventRepository.existsByProviderAndProviderEventId("SEPAY", "event-failed"))
                .thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("1001");
        assertThat(payment.getFailureMessage()).isEqualTo("Payment was declined");
        verify(paymentLedgerService, never()).synchronizeSuccessfulPayment(any());
    }

    @Test
    void verifiedSuccessStillSettlesCustomerCancelledAttempt() {
        Payment payment = payment();
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setFailureCode("CUSTOMER_CANCELLED");
        when(gatewayService.verifyCallback(callbackRequest())).thenReturn(successfulCallback(true));
        when(paymentEventRepository.existsByProviderAndProviderEventId("SEPAY", "event-1"))
                .thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderTxnId("4088878653")).thenReturn(Optional.empty());

        callbackService.handleCallback("sepay", callbackRequest(), "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getVerifiedAt()).isNotNull();
        verify(paymentLedgerService).synchronizeSuccessfulPayment(payment);
    }

    @Test
    void handleTrustedCallbackUsesSameVerifiedLedgerPipeline() {
        Payment payment = payment();
        payment.setProvider("MOCK_WALLET");
        PaymentGatewayCallback callback = mockWalletSuccessfulCallback();
        when(paymentEventRepository.existsByProviderAndProviderEventId("MOCK_WALLET", "mock-event-1"))
                .thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentCode("PAY-2026-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderTxnId("MOCK-TXN-001")).thenReturn(Optional.empty());

        callbackService.handleTrustedCallback(callback, RAW_PAYLOAD, "mock-wallet-simulator");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getVerifiedAt()).isNotNull();
        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("SIMULATOR_RESULT");
        verify(paymentLedgerService).synchronizeSuccessfulPayment(payment);
    }

    private PaymentGatewayCallback mockWalletSuccessfulCallback() {
        return new PaymentGatewayCallback(
                "MOCK_WALLET",
                "mock-event-1",
                "PAY-2026-001",
                "MOCK-TXN-001",
                new BigDecimal("1250000"),
                0,
                "Mock payment succeeded",
                "E_WALLET",
                true
        );
    }

    private Payment payment() {
        Payment payment = Payment.builder()
                .paymentCode("PAY-2026-001")
                .booking(booking())
                .provider("SEPAY")
                .amount(new BigDecimal("1250000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        payment.setId(10L);
        return payment;
    }

    private Booking booking() {
        Booking booking = Booking.builder()
                .publicId("booking-public-id")
                .bookingCode("BK-2026-000001")
                .build();
        booking.setId(1L);
        return booking;
    }

    private PaymentGatewayCallback successfulCallback(boolean signatureValid) {
        return new PaymentGatewayCallback(
                "SEPAY",
                signatureValid ? "event-1" : null,
                "PAY-2026-001",
                "4088878653",
                new BigDecimal("1250000"),
                0,
                "Successful.",
                "qr",
                signatureValid
        );
    }

    private PaymentGatewayCallbackRequest callbackRequest() {
        return new PaymentGatewayCallbackRequest(RAW_PAYLOAD, Map.of("X-Secret-Key", "test-secret"));
    }
}
