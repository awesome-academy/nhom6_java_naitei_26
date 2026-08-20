package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.BookingCalculatorService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookingCalculatorController {

    private final BookingCalculatorService bookingCalculatorService;

    public BookingCalculatorController(BookingCalculatorService bookingCalculatorService) {
        this.bookingCalculatorService = bookingCalculatorService;
    }

    @PostMapping(value = "/calculate-price", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<BookingPriceCalculationResponse> calculatePrice(
            @Valid @RequestBody BookingPriceCalculationRequest request
    ) {
        return ResponseEntity.ok(bookingCalculatorService.calculatePrice(request));
    }
}
