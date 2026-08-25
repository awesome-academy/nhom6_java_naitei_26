package com.example.hotelmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.mock-wallet")
@Getter
@Setter
public class MockWalletProperties {

    private boolean enabled;
    private String checkoutBaseUrl = "http://localhost:3000/payment/mock-wallet";
}
