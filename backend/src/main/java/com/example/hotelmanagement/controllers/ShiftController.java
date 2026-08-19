package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.shift.ShiftCreateRequest;
import com.example.hotelmanagement.dto.shift.ShiftResponse;
import com.example.hotelmanagement.dto.shift.ShiftUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.ShiftService;
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
@RequestMapping(value = "/api/shifts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.SHIFT_MANAGE)
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping
    public ResponseEntity<List<ShiftResponse>> getShifts() {
        return ResponseEntity.ok(shiftService.getShifts());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ShiftResponse> getShift(@PathVariable String code) {
        return ResponseEntity.ok(shiftService.getShift(code));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody ShiftCreateRequest request) {
        ShiftResponse response = shiftService.createShift(request);
        return ResponseEntity.created(URI.create("/api/shifts/" + response.code())).body(response);
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShiftResponse> updateShift(
            @PathVariable String code,
            @Valid @RequestBody ShiftUpdateRequest request
    ) {
        return ResponseEntity.ok(shiftService.updateShift(code, request));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteShift(@PathVariable String code) {
        shiftService.deleteShift(code);
        return ResponseEntity.noContent().build();
    }
}
