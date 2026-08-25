package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.booking.BookingCancelRequest;
import com.example.hotelmanagement.dto.booking.BookingCreateRequest;
import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomAssignmentResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeRequest;
import com.example.hotelmanagement.dto.booking.BookingRoomChangeResponse;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestCreateRequest;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestIdentityDocumentResponse;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.BookingGuestService;
import com.example.hotelmanagement.services.BookingRoomService;
import com.example.hotelmanagement.services.BookingService;
import com.example.hotelmanagement.services.BookingStateMachineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Bookings", description = "Create bookings and manage stay lifecycle, rooms, and guests.")
public class BookingController {

    private final BookingService bookingService;
    private final BookingStateMachineService bookingStateMachineService;
    private final BookingRoomService bookingRoomService;
    private final BookingGuestService bookingGuestService;

    public BookingController(
            BookingService bookingService,
            BookingStateMachineService bookingStateMachineService,
            BookingRoomService bookingRoomService,
            BookingGuestService bookingGuestService
    ) {
        this.bookingService = bookingService;
        this.bookingStateMachineService = bookingStateMachineService;
        this.bookingRoomService = bookingRoomService;
        this.bookingGuestService = bookingGuestService;
    }

    @Operation(summary = "Create Booking")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.createBooking(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/bookings/" + response.publicId())).body(response);
    }

    @Operation(summary = "Get My Bookings")
    @GetMapping("/me")
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookingService.getMyBookings(principal.getId()));
    }

    @Operation(summary = "Delete Pending Booking")
    @DeleteMapping("/{publicId}")
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<Void> deletePendingBooking(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        bookingService.deletePendingBooking(publicId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove Room From Pending Booking")
    @DeleteMapping("/{publicId}/rooms/{bookingRoomId}")
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public ResponseEntity<BookingResponse> removePendingBookingRoom(
            @PathVariable String publicId,
            @PathVariable Long bookingRoomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.removePendingBookingRoom(
                publicId,
                bookingRoomId,
                principal.getId()
        );
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check In")
    @PostMapping("/{publicId}/check-in")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public ResponseEntity<BookingResponse> checkIn(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookingStateMachineService.checkIn(publicId, principal.getId()));
    }

    @Operation(summary = "Check Out")
    @PostMapping("/{publicId}/check-out")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_OUT)
    public ResponseEntity<BookingResponse> checkOut(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookingStateMachineService.checkOut(publicId, principal.getId()));
    }

    @Operation(summary = "cancel")
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

    @Operation(summary = "assign Room")
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

    @Operation(summary = "Change Room")
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

    @Operation(summary = "Get Guests")
    @GetMapping("/{publicId}/guests")
    @PreAuthorize(PermissionExpressions.BOOKING_GUEST_MANAGE)
    public ResponseEntity<List<BookingGuestResponse>> getGuests(@PathVariable String publicId) {
        return ResponseEntity.ok(bookingGuestService.getGuests(publicId));
    }

    @Operation(summary = "Add Guest")
    @PostMapping(value = "/{publicId}/guests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_GUEST_MANAGE)
    public ResponseEntity<BookingGuestResponse> addGuest(
            @PathVariable String publicId,
            @Valid @RequestBody BookingGuestCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingGuestResponse response = bookingGuestService.addGuest(publicId, request, principal.getId());
        return ResponseEntity.created(URI.create(
                "/api/bookings/" + response.bookingPublicId() + "/guests/" + response.id()
        )).body(response);
    }

    @Operation(summary = "Reveal Guest Identity Document")
    @GetMapping("/{publicId}/guests/{guestId}/id-document")
    @PreAuthorize(PermissionExpressions.GUEST_READ_ID)
    public ResponseEntity<BookingGuestIdentityDocumentResponse> revealGuestIdentityDocument(
            @PathVariable String publicId,
            @PathVariable Long guestId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(bookingGuestService.revealIdentityDocument(
                publicId,
                guestId,
                principal.getId(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        ));
    }
}
