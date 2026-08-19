package com.example.hotelmanagement.exceptions;

import java.time.OffsetDateTime;

public class ShiftOverlapException extends RuntimeException {

    public ShiftOverlapException(String employeeCode, OffsetDateTime startAt, OffsetDateTime endAt) {
        super("Staff " + employeeCode + " already has an effective shift overlapping ["
                + startAt + ", " + endAt + ")");
    }
}
