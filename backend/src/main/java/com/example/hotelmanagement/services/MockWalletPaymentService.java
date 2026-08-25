package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.payment.MockWalletResult;
import com.example.hotelmanagement.dto.payment.MockWalletResultRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.payment.mock-wallet", name = "enabled", havingValue = "true")
public class MockWalletPaymentService {

    private static final int SUCCESS_RESULT_CODE = 0;
    private static final int FAILURE_RESULT_CODE = 1001;
    private static final String INTERNAL_LOOPBACK_IP = "127.0.0.1";

    private final PaymentService paymentService;
    private final PaymentCallbackService paymentCallbackService;
    private final ObjectMapper objectMapper;

    public MockWalletPaymentService(
            PaymentService paymentService,
            PaymentCallbackService paymentCallbackService,
            ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.paymentCallbackService = paymentCallbackService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize(PermissionExpressions.PAYMENT_CREATE)
    public PaymentStatusResponse submitResult(
            String bookingPublicId,
            String paymentCode,
            @Valid MockWalletResultRequest request,
            Long userId
    ) {
        PaymentStatusResponse currentPayment = paymentService.getPayment(
                bookingPublicId,
                paymentCode,
                userId
        );
        validatePaymentCanBeSimulated(currentPayment);

        boolean succeeded = request.result() == MockWalletResult.SUCCEEDED;
        PaymentGatewayCallback callback = new PaymentGatewayCallback(
                MockWalletPaymentGatewayService.PROVIDER_CODE,
                buildEventId(paymentCode, request.result()),
                paymentCode,
                succeeded ? "MOCK-TXN-" + paymentCode : null,
                currentPayment.amount(),
                succeeded ? SUCCESS_RESULT_CODE : FAILURE_RESULT_CODE,
                succeeded ? "Mock wallet payment succeeded" : "Mock wallet payment failed",
                PaymentMethod.E_WALLET.name(),
                true
        );
        paymentCallbackService.handleTrustedCallback(
                callback,
                buildRawPayload(currentPayment, request.result()),
                INTERNAL_LOOPBACK_IP
        );
        return paymentService.getPayment(bookingPublicId, paymentCode, userId);
    }

    private void validatePaymentCanBeSimulated(PaymentStatusResponse payment) {
        if (payment.method() != PaymentMethod.E_WALLET
                || !MockWalletPaymentGatewayService.PROVIDER_CODE.equals(payment.provider())) {
            throw new BusinessValidationException("Only mock wallet payments can use this endpoint");
        }
        if (payment.status() != PaymentStatus.PENDING
                && payment.status() != PaymentStatus.PROCESSING) {
            throw new BusinessValidationException("Mock wallet payment is no longer active");
        }
    }

    private String buildEventId(String paymentCode, MockWalletResult result) {
        return "MOCK-EVENT-" + paymentCode + "-" + result.name();
    }

    private String buildRawPayload(PaymentStatusResponse payment, MockWalletResult result) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("provider", MockWalletPaymentGatewayService.PROVIDER_CODE);
        payload.put("paymentCode", payment.paymentCode());
        payload.put("amount", payment.amount().toPlainString());
        payload.put("currency", payment.currency());
        payload.put("result", result.name());
        return payload.toString();
    }
}
