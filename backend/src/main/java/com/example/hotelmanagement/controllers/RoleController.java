package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.dto.rbac.RoleCreateRequest;
import com.example.hotelmanagement.dto.rbac.RolePermissionUpdateRequest;
import com.example.hotelmanagement.dto.rbac.RoleResponse;
import com.example.hotelmanagement.dto.rbac.RoleUpdateRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.RolePermissionService;
import com.example.hotelmanagement.services.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/roles", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    public RoleController(
        RoleService roleService,
        RolePermissionService rolePermissionService
    ) {
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.RBAC_READ)
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @GetMapping("/{code}")
    @PreAuthorize(PermissionExpressions.RBAC_READ)
    public ResponseEntity<RoleResponse> getRole(@PathVariable String code) {
        return ResponseEntity.ok(roleService.getRole(code));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        RoleResponse response = roleService.createRole(request);
        URI location = URI.create("/api/roles/" + response.code());
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<RoleResponse> updateRole(
        @PathVariable String code,
        @Valid @RequestBody RoleUpdateRequest request
    ) {
        return ResponseEntity.ok(roleService.updateRole(code, request));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<Void> deleteRole(@PathVariable String code) {
        roleService.deleteRole(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/permissions")
    @PreAuthorize(PermissionExpressions.RBAC_READ)
    public ResponseEntity<List<PermissionResponse>> getRolePermissions(@PathVariable String code) {
        return ResponseEntity.ok(rolePermissionService.getRolePermissions(code));
    }

    @PutMapping(value = "/{code}/permissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<RoleResponse> replaceRolePermissions(
        @PathVariable String code,
        @Valid @RequestBody RolePermissionUpdateRequest request
    ) {
        return ResponseEntity.ok(rolePermissionService.replaceRolePermissions(code, request));
    }

    @PostMapping("/{code}/permissions/{permissionCode}")
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<RoleResponse> addRolePermission(
        @PathVariable String code,
        @PathVariable String permissionCode
    ) {
        return ResponseEntity.ok(rolePermissionService.addRolePermission(code, permissionCode));
    }

    @DeleteMapping("/{code}/permissions/{permissionCode}")
    @PreAuthorize(PermissionExpressions.RBAC_MANAGE)
    public ResponseEntity<RoleResponse> removeRolePermission(
        @PathVariable String code,
        @PathVariable String permissionCode
    ) {
        return ResponseEntity.ok(rolePermissionService.removeRolePermission(code, permissionCode));
    }
}
