package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Shift Assignments", description = "Assign staff to shifts while preventing overlapping assignments.")
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;

    public ShiftAssignmentController(ShiftAssignmentService shiftAssignmentService) {
        this.shiftAssignmentService = shiftAssignmentService;
    }

    @Operation(summary = "Get Shift Assignments")
    @GetMapping
    public ResponseEntity<List<ShiftAssignmentResponse>> getShiftAssignments() {
        return ResponseEntity.ok(shiftAssignmentService.getShiftAssignments());
    }

    @Operation(summary = "Get Shift Assignment")
    @GetMapping("/{publicId}")
    public ResponseEntity<ShiftAssignmentResponse> getShiftAssignment(@PathVariable UUID publicId) {
        return ResponseEntity.ok(shiftAssignmentService.getShiftAssignment(publicId));
    }

    @Operation(summary = "Create Shift Assignment")
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

    @Operation(summary = "Update Shift Assignment")
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

    @Operation(summary = "Delete Shift Assignment")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteShiftAssignment(@PathVariable UUID publicId) {
        shiftAssignmentService.deleteShiftAssignment(publicId);
        return ResponseEntity.noContent().build();
    }
}
