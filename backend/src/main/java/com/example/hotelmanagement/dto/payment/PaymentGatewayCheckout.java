package com.example.hotelmanagement.dto.payment;

import java.util.List;

public record PaymentGatewayCheckout(
        String provider,
        String paymentUrl,
        String deeplink,
        String qrCodeValue,
        List<PaymentGatewayFormField> checkoutFields
) {
}
