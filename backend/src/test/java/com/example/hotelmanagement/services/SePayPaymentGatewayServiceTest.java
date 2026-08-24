package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.SePayProperties;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SePayPaymentGatewayServiceTest {

    private SePayPaymentGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        SePayProperties properties = new SePayProperties();
        properties.setMerchantId("MERCHANT_TEST");
        properties.setSecretKey("test-secret");
        properties.setSuccessUrl("https://app.example/success");
        properties.setErrorUrl("https://app.example/error");
        properties.setCancelUrl("https://app.example/cancel");
        gatewayService = new SePayPaymentGatewayService(
                properties,
                new SePaySignatureService(),
                new ObjectMapper()
        );
    }

    @Test
    void createCheckoutReturnsOrderedSignedPostFields() {
        PaymentGatewayCheckout checkout = gatewayService.createCheckout(payment());

        assertThat(checkout.provider()).isEqualTo("SEPAY");
        assertThat(checkout.paymentUrl()).isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
        assertThat(checkout.checkoutFields())
                .extracting(field -> field.name())
                .containsExactly(
                        "order_amount",
                        "merchant",
                        "currency",
                        "operation",
                        "order_description",
                        "order_invoice_number",
                        "payment_method",
                        "success_url",
                        "error_url",
                        "cancel_url",
                        "signature"
                );
        assertThat(checkout.checkoutFields().getLast().value())
                .isEqualTo("LhLSZY592wT5i8e5Jfmn7n7GW0V+YuEIJHnpPgNhUR4=");
    }

    @Test
    void verifyCallbackAcceptsAuthenticatedPaidOrder() {
        PaymentGatewayCallback callback = gatewayService.verifyCallback(new PaymentGatewayCallbackRequest(
                paidIpnPayload(),
                Map.of("X-Secret-Key", "test-secret")
        ));

        assertThat(callback.signatureValid()).isTrue();
        assertThat(callback.isSuccessful()).isTrue();
        assertThat(callback.paymentCode()).isEqualTo("PAY-2026-0123456789ABCDEF0123");
        assertThat(callback.providerTransactionId()).isEqualTo("SEPAY-TXN-001");
        assertThat(callback.amount()).isEqualByComparingTo("100000");
    }

    @Test
    void verifyCallbackRejectsWrongSecretKey() {
        PaymentGatewayCallback callback = gatewayService.verifyCallback(new PaymentGatewayCallbackRequest(
                paidIpnPayload(),
                Map.of("X-Secret-Key", "wrong-secret")
        ));

        assertThat(callback.signatureValid()).isFalse();
        assertThat(callback.providerEventId()).isNull();
    }

    private Payment payment() {
        Booking booking = Booking.builder()
                .bookingCode("BK-2026-000001")
                .build();
        return Payment.builder()
                .paymentCode("PAY-2026-0123456789ABCDEF0123")
                .booking(booking)
                .method(PaymentMethod.BANK_TRANSFER)
                .amount(new BigDecimal("100000.00"))
                .currency("VND")
                .build();
    }

    private String paidIpnPayload() {
        return """
                {
                  "timestamp": 1759134682,
                  "notification_type": "ORDER_PAID",
                  "order": {
                    "id": "SEPAY-ORDER-001",
                    "order_id": "SEPAY-ORDER-001",
                    "order_status": "CAPTURED",
                    "order_currency": "VND",
                    "order_amount": "100000.00",
                    "order_invoice_number": "PAY-2026-0123456789ABCDEF0123"
                  },
                  "transaction": {
                    "id": "SEPAY-EVENT-001",
                    "transaction_id": "SEPAY-TXN-001",
                    "transaction_status": "APPROVED",
                    "transaction_amount": "100000"
                  }
                }
                """;
    }
}
