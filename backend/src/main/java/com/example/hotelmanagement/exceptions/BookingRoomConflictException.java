package com.example.hotelmanagement.exceptions;

public class BookingRoomConflictException extends RuntimeException {

    public BookingRoomConflictException(String message) {
        super(message);
    }

    public BookingRoomConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
