package com.example.hotelmanagement.services;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SePaySignatureServiceTest {

    private final SePaySignatureService signatureService = new SePaySignatureService();

    @Test
    void signCheckoutFieldsCalculatesBase64HmacSha256InRequiredFieldOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", "100000");
        fields.put("merchant", "MERCHANT_TEST");
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "Payment for booking BK-2026-000001");
        fields.put("order_invoice_number", "PAY-2026-0123456789ABCDEF0123");
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("success_url", "https://app.example/success");
        fields.put("error_url", "https://app.example/error");
        fields.put("cancel_url", "https://app.example/cancel");

        assertThat(signatureService.signCheckoutFields(fields, "test-secret"))
                .isEqualTo("LhLSZY592wT5i8e5Jfmn7n7GW0V+YuEIJHnpPgNhUR4=");
    }

    @Test
    void matchesSecretKeyUsesExactValue() {
        assertThat(signatureService.matchesSecretKey("test-secret", "test-secret")).isTrue();
        assertThat(signatureService.matchesSecretKey("test-secret", "wrong-secret")).isFalse();
    }
}
