package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;

public interface PaymentGatewayService {

    String getProviderCode();

    boolean supports(PaymentMethod method);

    PaymentGatewayCheckout createCheckout(Payment payment);

    PaymentGatewayCallback verifyCallback(PaymentGatewayCallbackRequest callbackRequest);
}
