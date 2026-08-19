package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.rbac.RoleCreateRequest;
import com.example.hotelmanagement.dto.rbac.RoleResponse;
import com.example.hotelmanagement.dto.rbac.RoleUpdateRequest;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.repositories.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository);
    }

    @Test
    void createRoleNormalizesCodeAndCreatesCustomRole() {
        when(roleRepository.existsByCode("MANAGER")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = roleService.createRole(new RoleCreateRequest(
            " manager ",
            " Manager ",
            " Handles operations "
        ));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        Role savedRole = roleCaptor.getValue();

        assertThat(savedRole.getCode()).isEqualTo("MANAGER");
        assertThat(savedRole.getName()).isEqualTo("Manager");
        assertThat(savedRole.getDescription()).isEqualTo("Handles operations");
        assertThat(savedRole.getIsSystem()).isFalse();
        assertThat(response.code()).isEqualTo("MANAGER");
    }

    @Test
    void createRoleRejectsDuplicateCode() {
        when(roleRepository.existsByCode("MANAGER")).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(new RoleCreateRequest(
            "MANAGER",
            "Manager",
            null
        ))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateRoleAppliesPartialFields() {
        Role role = Role.builder().code("STAFF").name("Staff").isSystem(true).build();
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = roleService.updateRole("staff", new RoleUpdateRequest(
            " Front Desk Staff ",
            " "
        ));

        assertThat(response.name()).isEqualTo("Front Desk Staff");
        assertThat(response.description()).isNull();
    }

    @Test
    void deleteRoleRejectsSystemRole() {
        Role role = Role.builder().code("STAFF").name("Staff").isSystem(true).build();
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.deleteRole("STAFF"))
            .isInstanceOf(BusinessValidationException.class);
    }
}
