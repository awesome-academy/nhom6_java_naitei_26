package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentResponse;
import com.example.hotelmanagement.dto.shiftassignment.StaffShiftAbsenceRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.ShiftAssignmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/staff/shift-assignments", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;

    public StaffShiftAssignmentController(ShiftAssignmentService shiftAssignmentService) {
        this.shiftAssignmentService = shiftAssignmentService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.SHIFT_READ_OWN)
    public ResponseEntity<List<ShiftAssignmentResponse>> getOwnShiftAssignments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(shiftAssignmentService.getOwnShiftAssignments(principal.getId(), from, to));
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize(PermissionExpressions.SHIFT_UPDATE_OWN)
    public ResponseEntity<ShiftAssignmentResponse> completeShift(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(shiftAssignmentService.completeOwnShift(publicId, principal.getId()));
    }

    @PostMapping(value = "/{publicId}/absent", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.SHIFT_UPDATE_OWN)
    public ResponseEntity<ShiftAssignmentResponse> reportAbsence(
            @PathVariable UUID publicId,
            @Valid @RequestBody StaffShiftAbsenceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                shiftAssignmentService.reportOwnAbsence(publicId, principal.getId(), request.note())
        );
    }
}
