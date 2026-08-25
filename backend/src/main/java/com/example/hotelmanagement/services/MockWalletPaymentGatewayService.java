package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MockWalletProperties;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallback;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.dto.payment.PaymentGatewayCheckout;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.exceptions.InvalidPaymentCallbackException;
import com.example.hotelmanagement.exceptions.PaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

@Service
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.payment.mock-wallet", name = "enabled", havingValue = "true")
public class MockWalletPaymentGatewayService implements PaymentGatewayService {

    public static final String PROVIDER_CODE = "MOCK_WALLET";

    private static final Logger log = LoggerFactory.getLogger(MockWalletPaymentGatewayService.class);

    private final MockWalletProperties properties;

    public MockWalletPaymentGatewayService(MockWalletProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.E_WALLET;
    }

    @Override
    public PaymentGatewayCheckout createCheckout(Payment payment) {
        URI checkoutBaseUri = validateCheckoutBaseUri();
        String paymentUrl = UriComponentsBuilder.fromUri(checkoutBaseUri)
                .pathSegment(payment.getPaymentCode())
                .build()
                .encode()
                .toUriString();
        String qrCodeValue = String.join(
                "|",
                PROVIDER_CODE,
                payment.getPaymentCode(),
                payment.getAmount().toPlainString(),
                payment.getCurrency()
        );
        return new PaymentGatewayCheckout(
                PROVIDER_CODE,
                paymentUrl,
                null,
                qrCodeValue,
                List.of()
        );
    }

    @Override
    public PaymentGatewayCallback verifyCallback(PaymentGatewayCallbackRequest callbackRequest) {
        throw new InvalidPaymentCallbackException(
                "Mock wallet results must use the authenticated simulator endpoint"
        );
    }

    private URI validateCheckoutBaseUri() {
        try {
            URI checkoutBaseUri = new URI(properties.getCheckoutBaseUrl());
            String scheme = checkoutBaseUri.getScheme() == null
                    ? ""
                    : checkoutBaseUri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || checkoutBaseUri.getHost() == null
                    || checkoutBaseUri.getUserInfo() != null
                    || checkoutBaseUri.getQuery() != null
                    || checkoutBaseUri.getFragment() != null) {
                throw new PaymentGatewayException("Mock wallet checkout URL is invalid");
            }
            return checkoutBaseUri;
        } catch (URISyntaxException exception) {
            log.error("Mock wallet checkout URL is malformed", exception);
            throw new PaymentGatewayException("Mock wallet checkout URL is invalid", exception);
        }
    }
}
