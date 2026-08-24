package com.example.hotelmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentProperties {

    private String defaultProvider = "SEPAY";
}
