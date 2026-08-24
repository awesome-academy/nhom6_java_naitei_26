package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.staffprofile.StaffHireRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.StaffProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/staff-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Staff Profiles", description = "Manage staff hiring, profile updates, and deactivation.")
public class StaffProfileController {

    private final StaffProfileService staffProfileService;

    public StaffProfileController(StaffProfileService staffProfileService) {
        this.staffProfileService = staffProfileService;
    }

    @Operation(summary = "Hire Staff")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public ResponseEntity<StaffProfileResponse> hireStaff(@Valid @RequestBody StaffHireRequest request) {
        StaffProfileResponse response = staffProfileService.hireStaff(request);
        return ResponseEntity.created(URI.create("/api/staff-profiles/" + response.employeeCode())).body(response);
    }

    @Operation(summary = "Get Staff")
    @GetMapping("/{employeeCode}")
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public ResponseEntity<StaffProfileResponse> getStaff(@PathVariable String employeeCode) {
        return ResponseEntity.ok(staffProfileService.getStaff(employeeCode));
    }

    @Operation(summary = "Edit Staff")
    @PatchMapping(value = "/{employeeCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public ResponseEntity<StaffProfileResponse> editStaff(
            @PathVariable String employeeCode,
            @Valid @RequestBody StaffProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(staffProfileService.editStaff(employeeCode, request));
    }

    @Operation(summary = "Deactivate Staff")
    @DeleteMapping("/{employeeCode}")
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public ResponseEntity<Void> deactivateStaff(@PathVariable String employeeCode) {
        staffProfileService.deactivateStaff(employeeCode);
        return ResponseEntity.noContent().build();
    }
}
