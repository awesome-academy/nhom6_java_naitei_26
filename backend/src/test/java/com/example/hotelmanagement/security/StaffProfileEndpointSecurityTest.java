package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.StaffProfileService;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
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
}
