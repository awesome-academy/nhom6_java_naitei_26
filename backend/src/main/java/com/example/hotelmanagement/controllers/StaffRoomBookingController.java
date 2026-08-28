package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.room.RoomBookingMapResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.RoomBookingMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/admin/rooms", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Staff - Room Booking", description = "Room selection and booking timeline for staff-assisted bookings")
public class StaffRoomBookingController {

    private final RoomBookingMapService roomBookingMapService;

    public StaffRoomBookingController(RoomBookingMapService roomBookingMapService) {
        this.roomBookingMapService = roomBookingMapService;
    }

    @Operation(summary = "Get room booking map")
    @GetMapping("/booking-map")
    @PreAuthorize(PermissionExpressions.ROOM_BOOKING_MAP_READ)
    public ResponseEntity<List<RoomBookingMapResponse>> getBookingMap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate
    ) {
        return ResponseEntity.ok(roomBookingMapService.getBookingMap(checkInDate, checkOutDate));
    }
}
