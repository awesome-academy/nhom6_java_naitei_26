package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.StaffProfileService;
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
}
