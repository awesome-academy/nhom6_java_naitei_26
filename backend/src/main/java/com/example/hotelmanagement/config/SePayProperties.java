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

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public void setErrorUrl(String errorUrl) {
        this.errorUrl = errorUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
