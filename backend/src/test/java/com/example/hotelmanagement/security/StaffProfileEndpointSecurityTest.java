package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.StaffProfileService;
import com.example.hotelmanagement.dto.staffprofile.StaffOwnProfileResponse;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffProfileEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffProfileService staffProfileService;

    @Test
    void staffListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/staff-profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(staffProfileService);
    }

    @Test
    @WithMockUser
    void staffListRejectsUserWithoutStaffManagePermission() throws Exception {
        mockMvc.perform(get("/api/staff-profiles"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(staffProfileService);
    }

    @Test
    @WithMockUser(authorities = "staff:manage")
    void staffListAllowsStaffManagePermissionAndUsesActiveFilter() throws Exception {
        when(staffProfileService.getStaffProfiles(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/staff-profiles"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(staffProfileService).getStaffProfiles(true);
    }

    @Test
    @WithMockUser(authorities = "staff:manage")
    void managementEndpointDoesNotFallThroughToEmployeeCodeRoute() throws Exception {
        when(staffProfileService.getStaffManagementProfiles(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/staff-profiles/management"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(staffProfileService).getStaffManagementProfiles(false);
    }

    @Test
    @WithMockUser(authorities = "staff:manage")
    void statusEndpointAllowsStaffManagePermission() throws Exception {
        when(staffProfileService.updateEmploymentStatus("EMP-0001", EmploymentStatus.ON_LEAVE))
                .thenReturn(null);

        mockMvc.perform(patch("/api/staff-profiles/EMP-0001/status")
                        .contentType("application/json")
                        .content("{\"employmentStatus\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk());

        verify(staffProfileService).updateEmploymentStatus("EMP-0001", EmploymentStatus.ON_LEAVE);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void ownProfileRejectsCustomerRole() throws Exception {
        mockMvc.perform(get("/api/staff-profiles/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(staffProfileService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ownProfileRejectsAdminRole() throws Exception {
        mockMvc.perform(get("/api/staff-profiles/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(staffProfileService);
    }

    @Test
    void ownProfileUsesAuthenticatedStaffIdentity() throws Exception {
        when(staffProfileService.getOwnProfile(42L)).thenReturn(new StaffOwnProfileResponse(
                "EMP-0042", "Staff Member", "staff@example.com", null, null,
                "Receptionist", "Front Office", java.time.LocalDate.of(2026, 1, 1), EmploymentStatus.ACTIVE
        ));

        mockMvc.perform(get("/api/staff-profiles/me").with(user(staffPrincipal(42L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-0042"))
                .andExpect(jsonPath("$.baseSalary").doesNotExist());

        verify(staffProfileService).getOwnProfile(42L);
    }

    @Test
    void ownProfileUpdateRequiresPhoneProperty() throws Exception {
        mockMvc.perform(patch("/api/staff-profiles/me")
                        .with(user(staffPrincipal(42L)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(staffProfileService);
    }

    private UserPrincipal staffPrincipal(Long userId) {
        Role role = Role.builder().code("STAFF").build();
        User user = User.builder()
                .publicId("staff-public-id")
                .email("staff@example.com")
                .passwordHash("hash")
                .fullName("Staff Member")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(userId);
        user.getUserRoles().add(UserRole.builder().user(user).role(role).build());
        return UserPrincipal.from(user);
    }
}
