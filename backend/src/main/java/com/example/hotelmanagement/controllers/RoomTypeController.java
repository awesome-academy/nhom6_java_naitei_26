package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.roomtype.RoomTypeAmenitiesRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedsRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeCreateRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeStatsResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeUpdateRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.RoomTypeService;
import com.example.hotelmanagement.services.RoomTypeImageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/room-types", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Room Types", description = "Manage room types, beds, amenities, and images.")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;
    private final RoomTypeImageService roomTypeImageService;

    public RoomTypeController(
            RoomTypeService roomTypeService,
            RoomTypeImageService roomTypeImageService
    ) {
        this.roomTypeService = roomTypeService;
        this.roomTypeImageService = roomTypeImageService;
    }

    @Operation(summary = "Get Room Types")
    @GetMapping
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<List<RoomTypeResponse>> getRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getRoomTypes());
    }

    @Operation(summary = "Get Room Type Stats")
    @GetMapping("/stats")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<RoomTypeStatsResponse> getRoomTypeStats() {
        return ResponseEntity.ok(roomTypeService.getRoomTypeStats());
    }

    @Operation(summary = "Get Room Type")
    @GetMapping("/{code}")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<RoomTypeResponse> getRoomType(@PathVariable String code) {
        return ResponseEntity.ok(roomTypeService.getRoomType(code));
    }

    @Operation(summary = "Create Room Type")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public ResponseEntity<RoomTypeResponse> createRoomType(
            @Valid @RequestBody RoomTypeCreateRequest request
    ) {
        RoomTypeResponse response = roomTypeService.createRoomType(request);
        URI location = URI.create("/api/room-types/" + response.code());
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Update Room Type")
    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeUpdateRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(code, request));
    }

    @Operation(summary = "Delete Room Type")
    @DeleteMapping("/{code}")
    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public ResponseEntity<Void> deleteRoomType(@PathVariable String code) {
        roomTypeService.deleteRoomType(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Replace Room Type Beds")
    @PutMapping(value = "/{code}/beds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomTypeResponse> replaceRoomTypeBeds(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeBedsRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.replaceRoomTypeBeds(code, request));
    }

    @Operation(summary = "Replace Room Type Amenities")
    @PutMapping(value = "/{code}/amenities", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomTypeResponse> replaceRoomTypeAmenities(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeAmenitiesRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.replaceRoomTypeAmenities(code, request));
    }

    @Operation(summary = "Create Room Type Image Upload Url")
    @PostMapping(value = "/{code}/images/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomImageUploadUrlResponse> createRoomTypeImageUploadUrl(
            @PathVariable String code,
            @Valid @RequestBody RoomImageUploadUrlRequest request
    ) {
        return ResponseEntity.ok(roomTypeImageService.createUploadUrl(code, request));
    }

    @Operation(summary = "confirm Room Type Image Upload")
    @PostMapping(value = "/{code}/images/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomImageResponse> confirmRoomTypeImageUpload(
            @PathVariable String code,
            @Valid @RequestBody RoomImageConfirmRequest request
    ) {
        RoomImageResponse response = roomTypeImageService.confirmUpload(code, request);
        URI location = URI.create("/api/room-types/" + code.strip().toUpperCase(java.util.Locale.ROOT));
        return ResponseEntity.created(location).body(response);
    }
}
