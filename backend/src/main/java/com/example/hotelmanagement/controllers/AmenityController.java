package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.amenity.AmenityCreateRequest;
import com.example.hotelmanagement.dto.amenity.AmenityDetailResponse;
import com.example.hotelmanagement.dto.amenity.AmenityFilterOptionResponse;
import com.example.hotelmanagement.dto.amenity.AmenityUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.AmenityService;
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
@RequestMapping(value = "/api/amenities", produces = MediaType.APPLICATION_JSON_VALUE)
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<List<AmenityDetailResponse>> getAmenities() {
        return ResponseEntity.ok(amenityService.getAmenities());
    }

    @GetMapping("/filter-options")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<List<AmenityFilterOptionResponse>> getFilterOptions() {
        return ResponseEntity.ok(amenityService.getFilterOptions());
    }

    @GetMapping("/{code}")
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<AmenityDetailResponse> getAmenity(@PathVariable String code) {
        return ResponseEntity.ok(amenityService.getAmenity(code));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public ResponseEntity<AmenityDetailResponse> createAmenity(
            @Valid @RequestBody AmenityCreateRequest request
    ) {
        AmenityDetailResponse response = amenityService.createAmenity(request);
        return ResponseEntity.created(URI.create("/api/amenities/" + response.code())).body(response);
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<AmenityDetailResponse> updateAmenity(
            @PathVariable String code,
            @Valid @RequestBody AmenityUpdateRequest request
    ) {
        return ResponseEntity.ok(amenityService.updateAmenity(code, request));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public ResponseEntity<Void> deleteAmenity(@PathVariable String code) {
        amenityService.deleteAmenity(code);
        return ResponseEntity.noContent().build();
    }
}
