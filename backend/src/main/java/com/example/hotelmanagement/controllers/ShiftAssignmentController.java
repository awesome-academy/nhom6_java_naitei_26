package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentCreateRequest;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentResponse;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.ShiftAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/shift-assignments", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.SHIFT_MANAGE)
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;

    public ShiftAssignmentController(ShiftAssignmentService shiftAssignmentService) {
        this.shiftAssignmentService = shiftAssignmentService;
    }

    @GetMapping
    public ResponseEntity<List<ShiftAssignmentResponse>> getShiftAssignments() {
        return ResponseEntity.ok(shiftAssignmentService.getShiftAssignments());
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ShiftAssignmentResponse> getShiftAssignment(@PathVariable UUID publicId) {
        return ResponseEntity.ok(shiftAssignmentService.getShiftAssignment(publicId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShiftAssignmentResponse> createShiftAssignment(
            @Valid @RequestBody ShiftAssignmentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShiftAssignmentResponse response = shiftAssignmentService.createShiftAssignment(
                request,
                principal.getId()
        );
        URI location = URI.create("/api/shift-assignments/" + response.publicId());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShiftAssignmentResponse> updateShiftAssignment(
            @PathVariable UUID publicId,
            @Valid @RequestBody ShiftAssignmentUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(shiftAssignmentService.updateShiftAssignment(
                publicId,
                request,
                principal.getId()
        ));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteShiftAssignment(@PathVariable UUID publicId) {
        shiftAssignmentService.deleteShiftAssignment(publicId);
        return ResponseEntity.noContent().build();
    }
}
