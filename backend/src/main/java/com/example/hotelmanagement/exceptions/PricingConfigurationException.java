package com.example.hotelmanagement.exceptions;

public class PricingConfigurationException extends RuntimeException {

    public PricingConfigurationException(String message) {
        super(message);
    }

    public PricingConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
