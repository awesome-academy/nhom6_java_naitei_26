package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.email.BookingEmailRequest;
import com.example.hotelmanagement.dto.email.BookingEmailResponse;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.BookingEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.example.hotelmanagement.security.PermissionExpressions.EMAIL_SEND;

@RestController
@RequestMapping(value = "/api/bookings/{bookingPublicId}/emails", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(EMAIL_SEND)
@Tag(name = "Booking emails", description = "Queue and inspect booking-contact emails for authorized back-office users")
public class BookingEmailController {

    private final BookingEmailService bookingEmailService;

    public BookingEmailController(BookingEmailService bookingEmailService) {
        this.bookingEmailService = bookingEmailService;
    }

    @GetMapping
    @Operation(summary = "Get booking email history")
    public List<BookingEmailResponse> getBookingEmailHistory(@PathVariable String bookingPublicId) {
        return bookingEmailService.getBookingEmailHistory(bookingPublicId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Queue an email to the booking contact")
    public ResponseEntity<BookingEmailResponse> queueBookingEmail(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingEmailRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingEmailResponse response = bookingEmailService.queueBookingEmail(
                bookingPublicId,
                request,
                principal.getId()
        );
        return ResponseEntity.created(URI.create("/api/bookings/" + bookingPublicId + "/emails/" + response.id()))
                .body(response);
    }
}
