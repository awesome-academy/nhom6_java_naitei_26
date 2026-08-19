package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.PermissionResponse;
import com.example.hotelmanagement.entity.Permission;
import com.example.hotelmanagement.repositories.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository);
    }

    @Test
    void getPermissionsReturnsOrderedPermissionResponses() {
        Permission permission = createPermission("room:create", "room", "create");
        when(permissionRepository.findAllByOrderByResourceAscActionAscCodeAsc()).thenReturn(List.of(permission));

        List<PermissionResponse> responses = permissionService.getPermissions();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().code()).isEqualTo("room:create");
        assertThat(responses.getFirst().resource()).isEqualTo("room");
        assertThat(responses.getFirst().action()).isEqualTo("create");
    }

    @Test
    void getPermissionNormalizesCode() {
        Permission permission = createPermission("rbac:read", "rbac", "read");
        when(permissionRepository.findByCode("rbac:read")).thenReturn(Optional.of(permission));

        PermissionResponse response = permissionService.getPermission(" RBAC:READ ");

        assertThat(response.code()).isEqualTo("rbac:read");
    }

    private Permission createPermission(String code, String resource, String action) {
        return Permission.builder()
            .code(code)
            .resource(resource)
            .action(action)
            .description("Permission " + code)
            .build();
    }
}
