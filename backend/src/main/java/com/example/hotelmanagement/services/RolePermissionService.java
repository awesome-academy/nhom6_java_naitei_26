package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.dto.rbac.RolePermissionUpdateRequest;
import com.example.hotelmanagement.dto.rbac.RoleResponse;
import com.example.hotelmanagement.entity.Permission;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.RolePermission;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.PermissionRepository;
import com.example.hotelmanagement.repositories.RoleRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional
public class RolePermissionService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RolePermissionService(
        RoleRepository roleRepository,
        PermissionRepository permissionRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getRolePermissions(String roleCode) {
        return getExistingRole(roleCode).getRolePermissions()
            .stream()
            .map(RolePermission::getPermission)
            .sorted(Comparator.comparing(Permission::getCode))
            .map(this::mapPermissionResponse)
            .toList();
    }

    public RoleResponse replaceRolePermissions(String roleCode, @Valid RolePermissionUpdateRequest request) {
        Role role = getMutablePermissionsRole(roleCode);
        Set<String> permissionCodes = normalizePermissionCodes(request.permissionCodes());
        List<Permission> permissions = resolvePermissions(permissionCodes);

        role.getRolePermissions().clear();
        permissions.forEach(permission -> addPermission(role, permission));
        return mapRoleResponse(roleRepository.save(role));
    }

    public RoleResponse addRolePermission(String roleCode, String permissionCode) {
        Role role = getMutablePermissionsRole(roleCode);
        String normalizedPermissionCode = normalizePermissionCode(permissionCode);
        Permission permission = getExistingPermission(normalizedPermissionCode);

        boolean alreadyAssigned = role.getRolePermissions().stream()
            .anyMatch(rolePermission -> rolePermission.getPermission().getCode().equals(normalizedPermissionCode));
        if (!alreadyAssigned) {
            addPermission(role, permission);
        }
        return mapRoleResponse(roleRepository.save(role));
    }

    public RoleResponse removeRolePermission(String roleCode, String permissionCode) {
        Role role = getMutablePermissionsRole(roleCode);
        String normalizedPermissionCode = normalizePermissionCode(permissionCode);
        boolean removed = role.getRolePermissions().removeIf(rolePermission ->
            rolePermission.getPermission().getCode().equals(normalizedPermissionCode)
        );
        if (!removed) {
            throw new ResourceNotFoundException("Role permission", normalizeRoleCode(roleCode) + "/" + normalizedPermissionCode);
        }
        return mapRoleResponse(roleRepository.save(role));
    }

    private Role getMutablePermissionsRole(String roleCode) {
        Role role = getExistingRole(roleCode);
        if (ADMIN_ROLE_CODE.equals(role.getCode())) {
            throw new BusinessValidationException("ADMIN permissions cannot be changed");
        }
        return role;
    }

    private Role getExistingRole(String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return roleRepository.findByCode(normalizedRoleCode)
            .orElseThrow(() -> new ResourceNotFoundException("Role", normalizedRoleCode));
    }

    private Permission getExistingPermission(String permissionCode) {
        return permissionRepository.findByCode(permissionCode)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", permissionCode));
    }

    private List<Permission> resolvePermissions(Set<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            return List.of();
        }
        Map<String, Permission> permissionsByCode = permissionRepository.findAllByCodeIn(permissionCodes)
            .stream()
            .collect(Collectors.toMap(Permission::getCode, Function.identity()));
        List<String> missingCodes = permissionCodes.stream()
            .filter(permissionCode -> !permissionsByCode.containsKey(permissionCode))
            .toList();
        if (!missingCodes.isEmpty()) {
            throw new ResourceNotFoundException("Permissions", String.join(",", missingCodes));
        }
        return permissionCodes.stream()
            .map(permissionsByCode::get)
            .toList();
    }

    private void addPermission(Role role, Permission permission) {
        role.getRolePermissions().add(RolePermission.builder()
            .role(role)
            .permission(permission)
            .build());
    }

    private RoleResponse mapRoleResponse(Role role) {
        List<PermissionResponse> permissions = role.getRolePermissions().stream()
            .map(RolePermission::getPermission)
            .sorted(Comparator.comparing(Permission::getCode))
            .map(this::mapPermissionResponse)
            .toList();
        return new RoleResponse(
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getIsSystem(),
            permissions,
            role.getCreatedAt(),
            role.getUpdatedAt()
        );
    }

    private PermissionResponse mapPermissionResponse(Permission permission) {
        return new PermissionResponse(
            permission.getCode(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription()
        );
    }

    private Set<String> normalizePermissionCodes(Set<String> permissionCodes) {
        return permissionCodes.stream()
            .map(this::normalizePermissionCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRoleCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("Role", "");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizePermissionCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("Permission", "");
        }
        return code.strip().toLowerCase(Locale.ROOT);
    }
}
