package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.MockWalletResult;
import com.example.hotelmanagement.dto.payment.MockWalletResultRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockWalletPaymentServiceTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PAYMENT_CODE = "PAY-2026-001";
    private static final Long USER_ID = 42L;

    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentCallbackService paymentCallbackService;

    private MockWalletPaymentService mockWalletPaymentService;

    @BeforeEach
    void setUp() {
        mockWalletPaymentService = new MockWalletPaymentService(
                paymentService,
                paymentCallbackService,
                new ObjectMapper()
        );
    }

    @Test
    void submitSuccessfulResultUsesVerifiedCallbackPipeline() {
        when(paymentService.getPayment(BOOKING_PUBLIC_ID, PAYMENT_CODE, USER_ID))
                .thenReturn(response(PaymentStatus.PENDING), response(PaymentStatus.SUCCEEDED));

        PaymentStatusResponse result = mockWalletPaymentService.submitResult(
                BOOKING_PUBLIC_ID,
                PAYMENT_CODE,
                new MockWalletResultRequest(MockWalletResult.SUCCEEDED),
                USER_ID
        );

        ArgumentCaptor<PaymentGatewayCallback> callbackCaptor =
                ArgumentCaptor.forClass(PaymentGatewayCallback.class);
        verify(paymentCallbackService).handleTrustedCallback(
                callbackCaptor.capture(),
                anyString(),
                anyString()
        );
        assertThat(callbackCaptor.getValue().signatureValid()).isTrue();
        assertThat(callbackCaptor.getValue().isSuccessful()).isTrue();
        assertThat(callbackCaptor.getValue().provider()).isEqualTo("MOCK_WALLET");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    private PaymentStatusResponse response(PaymentStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-24T08:00:00Z");
        return new PaymentStatusResponse(
                PAYMENT_CODE,
                BOOKING_PUBLIC_ID,
                PaymentMethod.E_WALLET,
                new BigDecimal("375000.00"),
                "VND",
                status,
                "MOCK_WALLET",
                null,
                null,
                now.plusMinutes(10),
                false,
                now,
                now
        );
    }
}
