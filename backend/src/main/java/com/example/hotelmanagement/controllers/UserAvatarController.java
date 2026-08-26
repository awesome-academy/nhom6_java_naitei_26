package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.avatar.AvatarConfirmRequest;
import com.example.hotelmanagement.dto.avatar.AvatarResponse;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlRequest;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlResponse;
import com.example.hotelmanagement.services.UserAvatarService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/users/{publicId}/avatar", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    public UserAvatarController(UserAvatarService userAvatarService) {
        this.userAvatarService = userAvatarService;
    }

    @PostMapping(value = "/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvatarUploadUrlResponse> createUploadUrl(
            @PathVariable String publicId,
            @Valid @RequestBody AvatarUploadUrlRequest request
    ) {
        return ResponseEntity.ok(userAvatarService.createAdminUploadUrl(publicId, request));
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvatarResponse> confirmUpload(
            @PathVariable String publicId,
            @Valid @RequestBody AvatarConfirmRequest request
    ) {
        return ResponseEntity.ok(userAvatarService.confirmAdminUpload(publicId, request));
    }
}
