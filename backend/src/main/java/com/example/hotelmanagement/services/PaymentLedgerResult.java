package com.example.hotelmanagement.services;

public record PaymentLedgerResult(
        String bookingPublicId,
        boolean shouldConfirmBooking
) {
}
