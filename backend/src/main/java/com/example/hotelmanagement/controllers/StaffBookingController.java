package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.booking.*;
import com.example.hotelmanagement.dto.payment.PaymentCreateRequest;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.dto.payment.PaymentStatusResponse;
import com.example.hotelmanagement.dto.room.AvailableRoomForAssignmentResponse;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.BookingCalculatorService;
import com.example.hotelmanagement.services.BookingStaffService;
import com.example.hotelmanagement.services.BookingService;
import com.example.hotelmanagement.services.BookingStateMachineService;
import com.example.hotelmanagement.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Staff-facing booking management endpoints.
 * Provides list view, detail view, and staff actions.
 */
@RestController
@RequestMapping(value = "/api/admin/bookings", produces = "application/json")
@Tag(name = "Staff - Bookings", description = "Staff booking management operations")
public class StaffBookingController {

    private final BookingStaffService bookingStaffService;
    private final BookingStateMachineService bookingStateMachineService;
    private final BookingService bookingService;
    private final BookingCalculatorService bookingCalculatorService;
    private final PaymentService paymentService;

    public StaffBookingController(
            BookingStaffService bookingStaffService,
            BookingStateMachineService bookingStateMachineService,
            BookingService bookingService,
            BookingCalculatorService bookingCalculatorService,
            PaymentService paymentService
    ) {
        this.bookingStaffService = bookingStaffService;
        this.bookingStateMachineService = bookingStateMachineService;
        this.bookingService = bookingService;
        this.bookingCalculatorService = bookingCalculatorService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Create a staff-assisted booking")
    @PostMapping(consumes = "application/json")
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_CREATE)
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody StaffBookingCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.createStaffBooking(request, principal.getId());
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Calculate a staff booking price")
    @PostMapping(value = "/calculate-price", consumes = "application/json")
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_CREATE)
    public ResponseEntity<BookingPriceCalculationResponse> calculatePrice(
            @Valid @RequestBody StaffBookingPriceCalculationRequest request
    ) {
        return ResponseEntity.ok(bookingCalculatorService.calculateStaffPrice(request));
    }

    @Operation(summary = "Create a payment for a staff-assisted booking")
    @PostMapping(value = "/{publicId}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_PAYMENT)
    public ResponseEntity<PaymentResponse> createStaffPayment(
            @PathVariable String publicId,
            @Valid @RequestBody PaymentCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentResponse response = paymentService.createStaffPayment(
                publicId,
                request,
                idempotencyKey,
                principal.getId()
        );
        return ResponseEntity.created(URI.create(
                "/api/admin/bookings/" + publicId + "/payments/" + response.paymentCode()
        )).body(response);
    }

    @Operation(summary = "Get a staff-assisted booking payment")
    @GetMapping("/{publicId}/payments/{paymentCode}")
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_PAYMENT)
    public PaymentStatusResponse getStaffPayment(
            @PathVariable String publicId,
            @PathVariable @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$") String paymentCode,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return paymentService.getStaffPayment(publicId, paymentCode, principal.getId());
    }

    @Operation(summary = "Cancel a staff-assisted booking payment")
    @PostMapping("/{publicId}/payments/{paymentCode}/cancel")
    @PreAuthorize(PermissionExpressions.STAFF_BOOKING_PAYMENT)
    public PaymentStatusResponse cancelStaffPayment(
            @PathVariable String publicId,
            @PathVariable @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9-]+$") String paymentCode,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return paymentService.cancelStaffPayment(publicId, paymentCode, principal.getId());
    }

    @Operation(summary = "Get all bookings with filters (Staff view)")
    @GetMapping
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN + " or " + PermissionExpressions.BOOKING_CHECK_OUT)
    public ResponseEntity<BookingListResponse> getAllBookings(
            @Parameter(description = "Filter by statuses")
            @RequestParam(required = false) Set<BookingStatus> status,

            @Parameter(description = "Check-in date from (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate checkInFrom,

            @Parameter(description = "Check-in date to (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate checkInTo,

            @Parameter(description = "Check-out date from (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate checkOutFrom,

            @Parameter(description = "Check-out date to (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate checkOutTo,

            @Parameter(description = "Filter by booking source code")
            @RequestParam(required = false) String source,

            @Parameter(description = "Search by booking code, customer name, phone, email")
            @RequestParam(required = false) String search,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") Integer page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") Integer size
    ) {
        BookingListFilterRequest filter = BookingListFilterRequest.builder()
                .statuses(status)
                .checkInFrom(checkInFrom)
                .checkInTo(checkInTo)
                .checkOutFrom(checkOutFrom)
                .checkOutTo(checkOutTo)
                .sourceCode(source)
                .search(search)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(bookingStaffService.getAllBookings(filter));
    }

    @Operation(summary = "Get booking detail (Staff view)")
    @GetMapping("/{publicId}")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN + " or " + PermissionExpressions.BOOKING_CHECK_OUT)
    public ResponseEntity<BookingStaffDetailResponse> getBookingDetail(
            @PathVariable String publicId
    ) {
        return ResponseEntity.ok(bookingStaffService.getStaffBookingDetail(publicId));
    }

    @Operation(summary = "Confirm a pending booking")
    @PostMapping("/{publicId}/confirm")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public ResponseEntity<BookingConfirmResponse> confirmBooking(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingStaffService.confirmBooking(publicId, principal.getId())
        );
    }

    @Operation(summary = "Get available rooms for assignment")
    @GetMapping("/{publicId}/rooms/{bookingRoomId}/available-rooms")
    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public ResponseEntity<List<AvailableRoomForAssignmentResponse>> getAvailableRoomsForAssignment(
            @PathVariable String publicId,
            @PathVariable Long bookingRoomId,

            @Parameter(description = "Filter by floor")
            @RequestParam(required = false) Integer floor,

            @Parameter(description = "Filter by housekeeping status")
            @RequestParam(required = false) HousekeepingStatus housekeepingStatus,

            @Parameter(description = "Filter by view type")
            @RequestParam(required = false) RoomView viewType
    ) {
        return ResponseEntity.ok(
                bookingStaffService.getAvailableRoomsForAssignment(
                        publicId,
                        bookingRoomId,
                        floor,
                        housekeepingStatus,
                        viewType
                )
        );
    }

    @Operation(summary = "Get available floors for room assignment")
    @GetMapping("/{publicId}/rooms/{bookingRoomId}/available-floors")
    @PreAuthorize(PermissionExpressions.BOOKING_ASSIGN_ROOM)
    public ResponseEntity<List<Integer>> getAvailableFloors(
            @PathVariable String publicId,
            @PathVariable Long bookingRoomId
    ) {
        return ResponseEntity.ok(
                bookingStaffService.getAvailableFloors(publicId, bookingRoomId)
        );
    }

    @Operation(summary = "Check-in a booking")
    @PostMapping(value = "/{publicId}/check-in", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public ResponseEntity<BookingResponse> checkIn(
            @PathVariable String publicId,
            @Valid @RequestBody(required = false) BookingCheckInRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingStaffService.checkInBooking(publicId, request, principal.getId())
        );
    }

    @Operation(summary = "Check-out a booking")
    @PostMapping("/{publicId}/check-out")
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_OUT)
    public ResponseEntity<BookingResponse> checkOut(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingStateMachineService.checkOut(publicId, principal.getId())
        );
    }

    @Operation(summary = "Cancel a booking")
    @PostMapping(value = "/{publicId}/cancel", consumes = "application/json")
    @PreAuthorize(PermissionExpressions.BOOKING_CANCEL)
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable String publicId,
            @Valid @RequestBody BookingCancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                bookingStateMachineService.cancel(publicId, principal.getId(), request.reason())
        );
    }
}
