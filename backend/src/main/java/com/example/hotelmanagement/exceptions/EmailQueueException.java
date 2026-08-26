package com.example.hotelmanagement.exceptions;

public class EmailQueueException extends RuntimeException {

    public EmailQueueException(String message) {
        super(message);
    }

    public EmailQueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
