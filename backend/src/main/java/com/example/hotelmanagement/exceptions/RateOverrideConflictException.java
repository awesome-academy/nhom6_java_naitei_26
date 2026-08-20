package com.example.hotelmanagement.exceptions;

public class RateOverrideConflictException extends RuntimeException {

    private final Long conflictingRateOverrideId;

    public RateOverrideConflictException(Long conflictingRateOverrideId) {
        super("Rate override conflicts with active rate override: " + conflictingRateOverrideId);
        this.conflictingRateOverrideId = conflictingRateOverrideId;
    }

    public Long getConflictingRateOverrideId() {
        return conflictingRateOverrideId;
    }
}
