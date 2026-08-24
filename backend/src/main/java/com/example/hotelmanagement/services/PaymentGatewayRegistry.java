package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PaymentGatewayException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGatewayService> gateways;

    public PaymentGatewayRegistry(List<PaymentGatewayService> gatewayServices) {
        Map<String, PaymentGatewayService> registeredGateways = new HashMap<>();
        for (PaymentGatewayService gatewayService : gatewayServices) {
            String providerCode = normalizeProvider(gatewayService.getProviderCode());
            if (registeredGateways.put(providerCode, gatewayService) != null) {
                throw new PaymentGatewayException("Duplicate payment provider configuration");
            }
        }
        this.gateways = Map.copyOf(registeredGateways);
    }

    public PaymentGatewayService getGateway(String provider, PaymentMethod method) {
        PaymentGatewayService gateway = getGateway(provider);
        if (!gateway.supports(method)) {
            throw new BusinessValidationException(
                    "Payment method is not supported by provider " + gateway.getProviderCode()
            );
        }
        return gateway;
    }

    public PaymentGatewayService getGateway(String provider) {
        PaymentGatewayService gateway = gateways.get(normalizeProvider(provider));
        if (gateway == null) {
            throw new BusinessValidationException("Unsupported payment provider");
        }
        return gateway;
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessValidationException("Payment provider is required");
        }
        return provider.strip().toUpperCase(Locale.ROOT);
    }
}
