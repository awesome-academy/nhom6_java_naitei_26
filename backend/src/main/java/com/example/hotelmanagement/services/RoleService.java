package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.dto.rbac.RoleCreateRequest;
import com.example.hotelmanagement.dto.rbac.RoleResponse;
import com.example.hotelmanagement.dto.rbac.RoleUpdateRequest;
import com.example.hotelmanagement.entity.Permission;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.RolePermission;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RoleRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAllByOrderByCodeAsc()
            .stream()
            .map(this::mapRoleResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(String code) {
        return mapRoleResponse(getExistingRole(code));
    }

    public RoleResponse createRole(@Valid RoleCreateRequest request) {
        String normalizedCode = normalizeRoleCode(request.code());
        if (roleRepository.existsByCode(normalizedCode)) {
            throw new DuplicateResourceException("Role", "code", normalizedCode);
        }

        Role role = Role.builder()
            .code(normalizedCode)
            .name(normalizeRequiredText(request.name(), "Role name"))
            .description(normalizeOptionalText(request.description()))
            .isSystem(false)
            .build();
        return mapRoleResponse(roleRepository.save(role));
    }

    public RoleResponse updateRole(String code, @Valid RoleUpdateRequest request) {
        Role role = getExistingRole(code);
        if (request.name() != null) {
            role.setName(normalizeRequiredText(request.name(), "Role name"));
        }
        if (request.description() != null) {
            role.setDescription(normalizeOptionalText(request.description()));
        }
        return mapRoleResponse(roleRepository.save(role));
    }

    public void deleteRole(String code) {
        Role role = getExistingRole(code);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessValidationException("System roles cannot be deleted");
        }
        if (!role.getUserRoles().isEmpty()) {
            throw new BusinessValidationException("Roles assigned to users cannot be deleted");
        }
        roleRepository.delete(role);
    }

    private Role getExistingRole(String code) {
        String normalizedCode = normalizeRoleCode(code);
        return roleRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException("Role", normalizedCode));
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

    private String normalizeRoleCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Role code cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = value.strip();
        if (normalizedValue.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return normalizedValue;
    }

    private String normalizeOptionalText(String value) {
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }
}
