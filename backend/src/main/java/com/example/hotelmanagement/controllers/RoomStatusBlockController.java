package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockCreateRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockExtendRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.RoomStatusBlockService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/room-status-blocks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Room Status Blocks", description = "Manage maintenance and operational blocks for rooms.")
public class RoomStatusBlockController {

    private final RoomStatusBlockService roomStatusBlockService;

    public RoomStatusBlockController(RoomStatusBlockService roomStatusBlockService) {
        this.roomStatusBlockService = roomStatusBlockService;
    }

    @Operation(summary = "Get Blocks")
    @GetMapping
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public ResponseEntity<List<RoomStatusBlockResponse>> getBlocks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(roomStatusBlockService.getBlocks(startDate, endDate));
    }

    @Operation(summary = "Create Block")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomStatusBlockResponse> createBlock(
            @Valid @RequestBody RoomStatusBlockCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RoomStatusBlockResponse response = roomStatusBlockService.createBlock(request, principal.getId());
        URI location = URI.create("/api/room-status-blocks/" + response.publicId());
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Extend Block")
    @PatchMapping(value = "/{publicId}/extend", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<RoomStatusBlockResponse> extendBlock(
            @PathVariable UUID publicId,
            @Valid @RequestBody RoomStatusBlockExtendRequest request
    ) {
        return ResponseEntity.ok(roomStatusBlockService.extendBlock(publicId, request));
    }

    @Operation(summary = "Delete Block")
    @DeleteMapping("/{publicId}")
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public ResponseEntity<Void> deleteBlock(@PathVariable UUID publicId) {
        roomStatusBlockService.deleteBlock(publicId);
        return ResponseEntity.noContent().build();
    }
}
