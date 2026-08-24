package com.example.hotelmanagement.controllers;

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
@PreAuthorize(PermissionExpressions.POLICY_MANAGE)
public class CancellationPolicyController {

    private final CancellationPolicyService cancellationPolicyService;

    public CancellationPolicyController(CancellationPolicyService cancellationPolicyService) {
        this.cancellationPolicyService = cancellationPolicyService;
    }

    @GetMapping("/active")
    @PreAuthorize(PermissionExpressions.POLICY_USE_FOR_BOOKING)
    public ResponseEntity<List<CancellationPolicyResponse>> getActiveCancellationPolicies() {
        return ResponseEntity.ok(cancellationPolicyService.getActiveCancellationPolicies());
    }

    @GetMapping
    public ResponseEntity<List<CancellationPolicyResponse>> getCancellationPolicies() {
        return ResponseEntity.ok(cancellationPolicyService.getCancellationPolicies());
    }

    @GetMapping("/{code}")
    public ResponseEntity<CancellationPolicyResponse> getCancellationPolicy(@PathVariable String code) {
        return ResponseEntity.ok(cancellationPolicyService.getCancellationPolicy(code));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationPolicyResponse> createCancellationPolicy(
            @Valid @RequestBody CancellationPolicyCreateRequest request
    ) {
        CancellationPolicyResponse response = cancellationPolicyService.createCancellationPolicy(request);
        URI location = URI.create("/api/cancellation-policies/" + response.code());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationPolicyResponse> updateCancellationPolicy(
            @PathVariable String code,
            @Valid @RequestBody CancellationPolicyUpdateRequest request
    ) {
        return ResponseEntity.ok(cancellationPolicyService.updateCancellationPolicy(code, request));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteCancellationPolicy(@PathVariable String code) {
        cancellationPolicyService.deleteCancellationPolicy(code);
        return ResponseEntity.noContent().build();
    }
}
