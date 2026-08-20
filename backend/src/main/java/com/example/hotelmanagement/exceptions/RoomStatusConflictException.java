package com.example.hotelmanagement.exceptions;

public class RoomStatusConflictException extends RuntimeException {

    public RoomStatusConflictException(String message) {
        super(message);
    }

    public RoomStatusConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
