package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.avatar.AvatarConfirmRequest;
import com.example.hotelmanagement.dto.avatar.AvatarResponse;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlRequest;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlResponse;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.UserAvatarService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/staff-profiles/me/avatar", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('STAFF')")
public class StaffAvatarController {

    private final UserAvatarService userAvatarService;

    public StaffAvatarController(UserAvatarService userAvatarService) {
        this.userAvatarService = userAvatarService;
    }

    @PostMapping(value = "/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvatarUploadUrlResponse> createUploadUrl(
            @Valid @RequestBody AvatarUploadUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(userAvatarService.createStaffUploadUrl(principal.getId(), request));
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvatarResponse> confirmUpload(
            @Valid @RequestBody AvatarConfirmRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(userAvatarService.confirmStaffUpload(principal.getId(), request));
    }
}
