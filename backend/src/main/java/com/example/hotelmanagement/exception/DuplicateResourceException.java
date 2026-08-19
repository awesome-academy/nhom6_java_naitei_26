package com.example.hotelmanagement.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, String value) {
        super(resourceName + " already exists with " + fieldName + ": " + value);
    }
}
