package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.entity.Permission;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAllByOrderByResourceAscActionAscCodeAsc()
            .stream()
            .map(this::mapPermissionResponse)
            .toList();
    }

    public PermissionResponse getPermission(String code) {
        String normalizedCode = normalizePermissionCode(code);
        return permissionRepository.findByCode(normalizedCode)
            .map(this::mapPermissionResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", normalizedCode));
    }

    PermissionResponse mapPermissionResponse(Permission permission) {
        return new PermissionResponse(
            permission.getCode(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription()
        );
    }

    String normalizePermissionCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("Permission", "");
        }
        return code.strip().toLowerCase(Locale.ROOT);
    }
}
