package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.RolePermissionUpdateRequest;
import com.example.hotelmanagement.dto.rbac.RoleResponse;
import com.example.hotelmanagement.entity.Permission;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.RolePermission;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.PermissionRepository;
import com.example.hotelmanagement.repositories.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private RolePermissionService rolePermissionService;

    @BeforeEach
    void setUp() {
        rolePermissionService = new RolePermissionService(roleRepository, permissionRepository);
    }

    @Test
    void replaceRolePermissionsAssignsResolvedPermissions() {
        Role role = createRole("STAFF", true);
        Permission roomRead = createPermission("room:read");
        Permission shiftManage = createPermission("shift:manage");
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(role));
        when(permissionRepository.findAllByCodeIn(Set.of("room:read", "shift:manage")))
            .thenReturn(List.of(roomRead, shiftManage));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = rolePermissionService.replaceRolePermissions(
            " staff ",
            new RolePermissionUpdateRequest(Set.of("ROOM:READ", "shift:manage"))
        );

        assertThat(response.permissions())
            .extracting("code")
            .containsExactly("room:read", "shift:manage");
        assertThat(role.getRolePermissions()).hasSize(2);
    }

    @Test
    void addRolePermissionDoesNotDuplicateExistingPermission() {
        Role role = createRole("STAFF", true);
        Permission roomRead = createPermission("room:read");
        role.getRolePermissions().add(RolePermission.builder()
            .role(role)
            .permission(roomRead)
            .build());
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(role));
        when(permissionRepository.findByCode("room:read")).thenReturn(Optional.of(roomRead));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = rolePermissionService.addRolePermission("STAFF", "room:read");

        assertThat(response.permissions()).hasSize(1);
        assertThat(role.getRolePermissions()).hasSize(1);
    }

    @Test
    void replaceRolePermissionsRejectsAdminRole() {
        Role adminRole = createRole("ADMIN", true);
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));

        assertThatThrownBy(() -> rolePermissionService.replaceRolePermissions(
            "ADMIN",
            new RolePermissionUpdateRequest(Set.of("room:read"))
        )).isInstanceOf(BusinessValidationException.class);
    }

    private Role createRole(String code, boolean isSystem) {
        return Role.builder()
            .code(code)
            .name(code)
            .isSystem(isSystem)
            .build();
    }

    private Permission createPermission(String code) {
        return Permission.builder()
            .code(code)
            .resource(code.substring(0, code.indexOf(':')))
            .action(code.substring(code.indexOf(':') + 1))
            .description("Permission " + code)
            .build();
    }
}
