package com.example.hotelmanagement.controller;

import com.example.hotelmanagement.dto.roomtype.RoomTypeAmenitiesRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedsRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeCreateRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeUpdateRequest;
import com.example.hotelmanagement.service.RoomTypeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getRoomTypes());
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomTypeResponse> getRoomType(@PathVariable String code) {
        return ResponseEntity.ok(roomTypeService.getRoomType(code));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomTypeResponse> createRoomType(
            @Valid @RequestBody RoomTypeCreateRequest request
    ) {
        RoomTypeResponse response = roomTypeService.createRoomType(request);
        URI location = URI.create("/api/room-types/" + response.code());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeUpdateRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(code, request));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteRoomType(@PathVariable String code) {
        roomTypeService.deleteRoomType(code);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{code}/beds", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomTypeResponse> replaceRoomTypeBeds(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeBedsRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.replaceRoomTypeBeds(code, request));
    }

    @PutMapping(value = "/{code}/amenities", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomTypeResponse> replaceRoomTypeAmenities(
            @PathVariable String code,
            @Valid @RequestBody RoomTypeAmenitiesRequest request
    ) {
        return ResponseEntity.ok(roomTypeService.replaceRoomTypeAmenities(code, request));
    }
}
