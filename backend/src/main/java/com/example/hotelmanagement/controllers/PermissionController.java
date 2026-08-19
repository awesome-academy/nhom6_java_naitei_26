package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.PermissionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.RBAC_READ)
    public ResponseEntity<List<PermissionResponse>> getPermissions() {
        return ResponseEntity.ok(permissionService.getPermissions());
    }

    @GetMapping("/{code}")
    @PreAuthorize(PermissionExpressions.RBAC_READ)
    public ResponseEntity<PermissionResponse> getPermission(@PathVariable String code) {
        return ResponseEntity.ok(permissionService.getPermission(code));
    }
}
