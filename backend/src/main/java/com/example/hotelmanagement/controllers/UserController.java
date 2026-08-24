package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.user.UserResponse;
import com.example.hotelmanagement.dto.user.UserUpdateRequest;
import com.example.hotelmanagement.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "Manage user accounts. Administrative access is required.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get Users")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @Operation(summary = "Get User")
    @GetMapping("/{publicId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String publicId) {
        return ResponseEntity.ok(userService.getUser(publicId));
    }

    @Operation(summary = "Update User")
    @PatchMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable String publicId,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(publicId, request));
    }

    @Operation(summary = "Delete User")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String publicId) {
        userService.deleteUser(publicId);
        return ResponseEntity.noContent().build();
    }
}
