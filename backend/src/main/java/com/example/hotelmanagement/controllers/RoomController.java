package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.room.HousekeepingStatusUpdateRequest;
import com.example.hotelmanagement.dto.room.RoomCreateRequest;
import com.example.hotelmanagement.dto.room.RoomOperationalStatusResponse;
import com.example.hotelmanagement.dto.room.RoomOperationalStatusUpdateRequest;
import com.example.hotelmanagement.dto.room.RoomResponse;
import com.example.hotelmanagement.dto.room.RoomUpdateRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageOrderRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.RoomImageService;
import com.example.hotelmanagement.services.RoomService;
import com.example.hotelmanagement.services.RoomStatusBlockService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/rooms", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Rooms", description = "Manage physical rooms, housekeeping, operational status, and images.")
public class RoomController {

    private final RoomService roomService;
    private final RoomImageService roomImageService;
    private final RoomStatusBlockService roomStatusBlockService;

    public RoomController(
            RoomService roomService,
            RoomImageService roomImageService,
            RoomStatusBlockService roomStatusBlockService
    ) {
        this.roomService = roomService;
        this.roomImageService = roomImageService;
        this.roomStatusBlockService = roomStatusBlockService;
    }

    @Operation(summary = "Get Rooms")
    @GetMapping
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<List<RoomResponse>> getRooms(
            @RequestParam(required = false) String roomTypeCode,
            @RequestParam(required = false) RoomView viewType,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) List<String> amenityCodes
    ) {
        return ResponseEntity.ok(roomService.getRooms(roomTypeCode, viewType, floor, amenityCodes));
    }

    @Operation(summary = "Get Room")
    @GetMapping("/{roomNumber}")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomNumber) {
        return ResponseEntity.ok(roomService.getRoom(roomNumber));
    }

    @Operation(summary = "Create Room")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody RoomCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RoomResponse response = roomService.createRoom(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/rooms/" + response.roomNumber())).body(response);
    }

    @Operation(summary = "Update Room")
    @PutMapping(value = "/{roomNumber}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable String roomNumber,
            @Valid @RequestBody RoomUpdateRequest request
    ) {
        return ResponseEntity.ok(roomService.updateRoom(roomNumber, request));
    }

    @Operation(summary = "Delete Room")
    @DeleteMapping("/{roomNumber}")
    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomNumber) {
        roomService.deleteRoom(roomNumber);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update Housekeeping Status")
    @PatchMapping(value = "/{roomNumber}/housekeeping-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomResponse> updateHousekeepingStatus(
            @PathVariable String roomNumber,
            @Valid @RequestBody HousekeepingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(roomService.updateHousekeepingStatus(roomNumber, request));
    }

    @Operation(summary = "Update Operational Status")
    @PatchMapping(value = "/{roomNumber}/operational-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomOperationalStatusResponse> updateOperationalStatus(
            @PathVariable String roomNumber,
            @Valid @RequestBody RoomOperationalStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(roomStatusBlockService.updateOperationalStatus(roomNumber, request));
    }

    @Operation(summary = "Create Room Image Upload Url")
    @PostMapping(value = "/{roomNumber}/images/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomImageUploadUrlResponse> createRoomImageUploadUrl(
            @PathVariable String roomNumber,
            @Valid @RequestBody RoomImageUploadUrlRequest request
    ) {
        return ResponseEntity.ok(roomImageService.createUploadUrl(roomNumber, request));
    }

    @Operation(summary = "confirm Room Image Upload")
    @PostMapping(value = "/{roomNumber}/images/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomImageResponse> confirmRoomImageUpload(
            @PathVariable String roomNumber,
            @Valid @RequestBody RoomImageConfirmRequest request
    ) {
        RoomImageResponse response = roomImageService.confirmUpload(roomNumber, request);
        URI location = URI.create("/api/rooms/" + roomNumber.strip().toUpperCase(java.util.Locale.ROOT));
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "reorder Room Images")
    @PutMapping(value = "/{roomNumber}/images/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<List<RoomImageResponse>> reorderRoomImages(
            @PathVariable String roomNumber,
            @Valid @RequestBody RoomImageOrderRequest request
    ) {
        return ResponseEntity.ok(roomImageService.reorderImages(roomNumber, request));
    }
}
