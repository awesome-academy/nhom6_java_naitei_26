package com.example.hotelmanagement.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(resourceName + " not found: " + identifier);
    }
}
