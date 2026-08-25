package com.example.hotelmanagement.exceptions;

public class InvalidPaymentCallbackException extends RuntimeException {

    public InvalidPaymentCallbackException(String message) {
        super(message);
    }

    public InvalidPaymentCallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
