package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MockWalletProperties;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.exceptions.InvalidPaymentCallbackException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockWalletPaymentGatewayServiceTest {

    private MockWalletPaymentGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        MockWalletProperties properties = new MockWalletProperties();
        properties.setCheckoutBaseUrl("http://localhost:3000/payment/mock-wallet");
        gatewayService = new MockWalletPaymentGatewayService(properties);
    }

    @Test
    void createCheckoutReturnsMockPageAndQrValue() {
        var checkout = gatewayService.createCheckout(payment());

        assertThat(checkout.provider()).isEqualTo("MOCK_WALLET");
        assertThat(checkout.paymentUrl())
                .isEqualTo("http://localhost:3000/payment/mock-wallet/PAY-2026-001");
        assertThat(checkout.qrCodeValue())
                .isEqualTo("MOCK_WALLET|PAY-2026-001|375000.00|VND");
        assertThat(checkout.checkoutFields()).isEmpty();
    }

    @Test
    void gatewayOnlySupportsElectronicWallet() {
        assertThat(gatewayService.supports(PaymentMethod.E_WALLET)).isTrue();
        assertThat(gatewayService.supports(PaymentMethod.CARD)).isFalse();
    }

    @Test
    void publicCallbackCannotForgeMockWalletResult() {
        PaymentGatewayCallbackRequest callbackRequest = new PaymentGatewayCallbackRequest(
                "{\"result\":\"SUCCEEDED\"}",
                Map.of()
        );

        assertThatThrownBy(() -> gatewayService.verifyCallback(callbackRequest))
                .isInstanceOf(InvalidPaymentCallbackException.class)
                .hasMessageContaining("authenticated simulator endpoint");
    }

    private Payment payment() {
        return Payment.builder()
                .paymentCode("PAY-2026-001")
                .booking(Booking.builder().bookingCode("BK-2026-001").build())
                .method(PaymentMethod.E_WALLET)
                .amount(new BigDecimal("375000.00"))
                .currency("VND")
                .build();
    }
}
