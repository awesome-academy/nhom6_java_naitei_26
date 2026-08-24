package com.example.hotelmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.sepay")
@Getter
@Setter
public class SePayProperties {

    private String checkoutEndpoint = "https://pay-sandbox.sepay.vn/v1/checkout/init";
    private String merchantId;
    private String secretKey;
    private String successUrl;
    private String errorUrl;
    private String cancelUrl;
}
