package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyCreateRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.CancellationPolicyService;
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
@RequestMapping(value = "/api/cancellation-policies", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cancellation Policies", description = "Manage cancellation policies and refund tiers.")
public class CancellationPolicyController {

    private final CancellationPolicyService cancellationPolicyService;

    public CancellationPolicyController(CancellationPolicyService cancellationPolicyService) {
        this.cancellationPolicyService = cancellationPolicyService;
    }

    @Operation(summary = "Get Active Cancellation Policies")
    @GetMapping("/active")
    @PreAuthorize(PermissionExpressions.POLICY_USE_FOR_BOOKING)
    public ResponseEntity<List<CancellationPolicyResponse>> getActiveCancellationPolicies() {
        return ResponseEntity.ok(cancellationPolicyService.getActiveCancellationPolicies());
    }

    @Operation(summary = "Get Cancellation Policies")
    @GetMapping
    @PreAuthorize(PermissionExpressions.POLICY_USE_FOR_BOOKING)
    public ResponseEntity<List<CancellationPolicyResponse>> getCancellationPolicies() {
        return ResponseEntity.ok(cancellationPolicyService.getCancellationPolicies());
    }

    @Operation(summary = "Get Cancellation Policy")
    @GetMapping("/{code}")
    @PreAuthorize(PermissionExpressions.POLICY_USE_FOR_BOOKING)
    public ResponseEntity<CancellationPolicyResponse> getCancellationPolicy(@PathVariable String code) {
        return ResponseEntity.ok(cancellationPolicyService.getCancellationPolicy(code));
    }

    @Operation(summary = "Create Cancellation Policy")
    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationPolicyResponse> createCancellationPolicy(
            @Valid @RequestBody CancellationPolicyCreateRequest request
    ) {
        CancellationPolicyResponse response = cancellationPolicyService.createCancellationPolicy(request);
        URI location = URI.create("/api/cancellation-policies/" + response.code());
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Update Cancellation Policy")
    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationPolicyResponse> updateCancellationPolicy(
            @PathVariable String code,
            @Valid @RequestBody CancellationPolicyUpdateRequest request
    ) {
        return ResponseEntity.ok(cancellationPolicyService.updateCancellationPolicy(code, request));
    }

    @Operation(summary = "Delete Cancellation Policy")
    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteCancellationPolicy(@PathVariable String code) {
        cancellationPolicyService.deleteCancellationPolicy(code);
        return ResponseEntity.noContent().build();
    }
}
