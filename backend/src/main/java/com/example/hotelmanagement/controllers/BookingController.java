package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.booking.BookingCancelRequest;
import com.example.hotelmanagement.dto.booking.BookingCreateRequest;
import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomAssignmentResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeRequest;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.BookingRoomService;
import com.example.hotelmanagement.services.BookingService;
import com.example.hotelmanagement.services.BookingStateMachineService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookingController {

    private final BookingService bookingService;
    private final BookingStateMachineService bookingStateMachineService;
    private final BookingRoomService bookingRoomService;

    public BookingController(
            BookingService bookingService,
            BookingStateMachineService bookingStateMachineService,
            BookingRoomService bookingRoomService
    ) {
        this.bookingService = bookingService;
        this.bookingStateMachineService = bookingStateMachineService;
        this.bookingRoomService = bookingRoomService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.createBooking(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/bookings/" + response.publicId())).body(response);
    }

    @PostMapping("/{publicId}/check-in")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public ResponseEntity<BookingResponse> checkIn(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookingStateMachineService.checkIn(publicId, principal.getId()));
    }

    @PostMapping("/{publicId}/check-out")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_OUT)
    public ResponseEntity<BookingResponse> checkOut(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookingStateMachineService.checkOut(publicId, principal.getId()));
    }

    @PostMapping(value = "/{publicId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_CANCEL)
    public ResponseEntity<BookingResponse> cancel(
            @PathVariable String publicId,
            @Valid @RequestBody BookingCancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingStateMachineService.cancel(publicId, principal.getId(), request.reason())
        );
    }

    @PostMapping("/{publicId}/rooms/{bookingRoomId}/assign")
    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public ResponseEntity<BookingRoomAssignmentResponse> assignRoom(
            @PathVariable String publicId,
            @PathVariable Long bookingRoomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingRoomService.assignRoom(publicId, bookingRoomId, principal.getId())
        );
    }

    @PostMapping(value = "/{publicId}/rooms/{bookingRoomId}/change-room", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public ResponseEntity<BookingRoomChangeResponse> changeRoom(
            @PathVariable String publicId,
            @PathVariable Long bookingRoomId,
            @Valid @RequestBody BookingRoomChangeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingRoomService.changeRoom(publicId, bookingRoomId, request, principal.getId())
        );
    }
}
