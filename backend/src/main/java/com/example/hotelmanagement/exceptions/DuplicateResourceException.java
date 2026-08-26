package com.example.hotelmanagement.exceptions;

import java.util.Map;

public class DuplicateResourceException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public DuplicateResourceException(String resourceName, String fieldName, String value) {
        this(resourceName + " already exists with " + fieldName + ": " + value, Map.of());
    }

    public DuplicateResourceException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
