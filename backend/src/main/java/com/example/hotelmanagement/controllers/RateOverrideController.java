package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.pricing.RateOverrideCreateRequest;
import com.example.hotelmanagement.dto.pricing.RateOverrideResponse;
import com.example.hotelmanagement.dto.pricing.RateOverrideUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.RateOverrideService;
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
@RequestMapping(value = "/api/rate-overrides", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.PRICING_MANAGE)
public class RateOverrideController {

    private final RateOverrideService rateOverrideService;

    public RateOverrideController(RateOverrideService rateOverrideService) {
        this.rateOverrideService = rateOverrideService;
    }

    @GetMapping
    public ResponseEntity<List<RateOverrideResponse>> getActiveRateOverrides() {
        return ResponseEntity.ok(rateOverrideService.getActiveRateOverrides());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RateOverrideResponse> getRateOverride(@PathVariable Long id) {
        return ResponseEntity.ok(rateOverrideService.getRateOverride(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RateOverrideResponse> createRateOverride(
            @Valid @RequestBody RateOverrideCreateRequest request
    ) {
        RateOverrideResponse response = rateOverrideService.createRateOverride(request);
        return ResponseEntity.created(
                URI.create("/api/rate-overrides/" + response.id())
        ).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RateOverrideResponse> updateRateOverride(
            @PathVariable Long id,
            @Valid @RequestBody RateOverrideUpdateRequest request
    ) {
        return ResponseEntity.ok(rateOverrideService.updateRateOverride(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRateOverride(@PathVariable Long id) {
        rateOverrideService.deleteRateOverride(id);
        return ResponseEntity.noContent().build();
    }
}
