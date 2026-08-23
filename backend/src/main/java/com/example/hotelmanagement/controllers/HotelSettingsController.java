package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsResponse;
import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.HotelSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/hotel-settings", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.SETTINGS_MANAGE)
public class HotelSettingsController {

    private final HotelSettingsService hotelSettingsService;

    public HotelSettingsController(HotelSettingsService hotelSettingsService) {
        this.hotelSettingsService = hotelSettingsService;
    }

    @GetMapping
    public ResponseEntity<HotelSettingsResponse> getSettings() {
        return ResponseEntity.ok(hotelSettingsService.getSettings());
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HotelSettingsResponse> updateSettings(
            @Valid @RequestBody HotelSettingsUpdateRequest request
    ) {
        return ResponseEntity.ok(hotelSettingsService.updateSettings(request));
    }
}
