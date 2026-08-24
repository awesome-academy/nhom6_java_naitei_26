package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.SePayProperties;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.dto.payment.PaymentGatewayFormField;
import com.example.hotelmanagement.dto.payment.sepay.SePayIpnRequest;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.exceptions.InvalidPaymentCallbackException;
import com.example.hotelmanagement.exceptions.PaymentGatewayException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SePayPaymentGatewayService implements PaymentGatewayService {

    public static final String PROVIDER_CODE = "SEPAY";

    private static final Logger log = LoggerFactory.getLogger(SePayPaymentGatewayService.class);
    private static final String OPERATION_PURCHASE = "PURCHASE";
    private static final String STATUS_CAPTURED = "CAPTURED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String NOTIFICATION_ORDER_PAID = "ORDER_PAID";
    private static final String WEBHOOK_SECRET_HEADER = "X-Secret-Key";
    private static final Set<String> ALLOWED_CHECKOUT_HOSTS = Set.of(
            "pay-sandbox.sepay.vn",
            "pay.sepay.vn"
    );
    private static final Set<PaymentMethod> SUPPORTED_METHODS = Set.of(
            PaymentMethod.INTERNET_BANKING,
            PaymentMethod.CARD,
            PaymentMethod.BANK_TRANSFER
    );

    private final SePayProperties properties;
    private final SePaySignatureService signatureService;
    private final ObjectMapper objectMapper;

    public SePayPaymentGatewayService(
            SePayProperties properties,
            SePaySignatureService signatureService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return SUPPORTED_METHODS.contains(method);
    }

    @Override
    public PaymentGatewayCheckout createCheckout(Payment payment) {
        validateConfiguration();
        Map<String, String> fields = buildCheckoutFields(payment);
        String signature = signatureService.signCheckoutFields(fields, properties.getSecretKey());
        List<PaymentGatewayFormField> formFields = new ArrayList<>();
        fields.forEach((name, value) -> formFields.add(new PaymentGatewayFormField(name, value)));
        formFields.add(new PaymentGatewayFormField("signature", signature));
        return new PaymentGatewayCheckout(
                PROVIDER_CODE,
                validateCheckoutEndpoint().toString(),
                null,
                null,
                List.copyOf(formFields)
        );
    }

    @Override
    public PaymentGatewayCallback verifyCallback(PaymentGatewayCallbackRequest callbackRequest) {
        validateConfiguration();
        SePayIpnRequest request;
        try {
            request = objectMapper.readValue(callbackRequest.rawPayload(), SePayIpnRequest.class);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to parse SePay callback payload errorType={}", exception.getClass().getSimpleName());
            throw new InvalidPaymentCallbackException("Malformed SePay callback", exception);
        }

        boolean hasRequiredFields = hasRequiredCallbackFields(request);
        boolean secretKeyMatches = signatureService.matchesSecretKey(
                properties.getSecretKey(),
                callbackRequest.headerValue(WEBHOOK_SECRET_HEADER)
        );
        boolean callbackDataMatches = hasRequiredFields
                && "VND".equalsIgnoreCase(request.order().orderCurrency())
                && request.order().orderAmount().compareTo(request.transaction().transactionAmount()) == 0;
        boolean signatureValid = callbackDataMatches && secretKeyMatches;
        boolean successful = signatureValid
                && NOTIFICATION_ORDER_PAID.equals(request.notificationType())
                && STATUS_CAPTURED.equals(request.order().orderStatus())
                && STATUS_APPROVED.equals(request.transaction().transactionStatus());

        return new PaymentGatewayCallback(
                PROVIDER_CODE,
                signatureValid ? buildProviderEventId(request) : null,
                request.order() == null ? null : request.order().orderInvoiceNumber(),
                providerTransactionId(request),
                request.order() == null ? null : request.order().orderAmount(),
                successful ? 0 : 1,
                request.notificationType(),
                "BANK_TRANSFER",
                signatureValid
        );
    }

    private Map<String, String> buildCheckoutFields(Payment payment) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", toVndAmount(payment.getAmount()));
        fields.put("merchant", properties.getMerchantId());
        fields.put("currency", "VND");
        fields.put("operation", OPERATION_PURCHASE);
        fields.put("order_description", "Payment for booking " + payment.getBooking().getBookingCode());
        fields.put("order_invoice_number", payment.getPaymentCode());
        fields.put("payment_method", resolveSePayPaymentMethod(payment.getMethod()));
        fields.put("success_url", properties.getSuccessUrl());
        fields.put("error_url", properties.getErrorUrl());
        fields.put("cancel_url", properties.getCancelUrl());
        return fields;
    }

    private String resolveSePayPaymentMethod(PaymentMethod method) {
        return method == PaymentMethod.CARD ? "CARD" : "BANK_TRANSFER";
    }

    private String toVndAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            log.warn("SePay payment amount is not a whole-number VND amount={}", amount, exception);
            throw new PaymentGatewayException("SePay requires a whole-number VND amount", exception);
        }
    }

    private URI validateCheckoutEndpoint() {
        try {
            URI endpoint = new URI(properties.getCheckoutEndpoint());
            String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || !ALLOWED_CHECKOUT_HOSTS.contains(host)) {
                throw new PaymentGatewayException("SePay endpoint must use an approved HTTPS host");
            }
            return endpoint;
        } catch (URISyntaxException exception) {
            log.error("SePay checkout endpoint is malformed", exception);
            throw new PaymentGatewayException("SePay endpoint configuration is invalid", exception);
        }
    }

    private void validateConfiguration() {
        if (isBlank(properties.getMerchantId())
                || isBlank(properties.getSecretKey())
                || isBlank(properties.getSuccessUrl())
                || isBlank(properties.getErrorUrl())
                || isBlank(properties.getCancelUrl())) {
            throw new PaymentGatewayException("SePay gateway credentials or callback URLs are not configured");
        }
    }

    private boolean hasRequiredCallbackFields(SePayIpnRequest request) {
        return request != null
                && request.notificationType() != null
                && request.order() != null
                && request.order().orderInvoiceNumber() != null
                && request.order().orderAmount() != null
                && request.order().orderCurrency() != null
                && request.transaction() != null
                && request.transaction().id() != null
                && request.transaction().transactionAmount() != null
                && providerTransactionId(request) != null;
    }

    private String providerTransactionId(SePayIpnRequest request) {
        if (request == null || request.transaction() == null) {
            return null;
        }
        String transactionId = request.transaction().transactionId();
        return isBlank(transactionId) ? request.transaction().id() : transactionId;
    }

    private String buildProviderEventId(SePayIpnRequest request) {
        return request.transaction().id() + ":" + request.notificationType();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
