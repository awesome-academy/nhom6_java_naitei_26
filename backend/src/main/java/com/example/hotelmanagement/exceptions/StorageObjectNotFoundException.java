package com.example.hotelmanagement.exceptions;

public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
