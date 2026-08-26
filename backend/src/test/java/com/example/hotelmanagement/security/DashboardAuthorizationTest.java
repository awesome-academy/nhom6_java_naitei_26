package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.dashboard.DashboardOverviewResponse;
import com.example.hotelmanagement.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser(authorities = "booking:read_any")
    void unrelatedPermissionCannotReadDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser(authorities = "dashboard:read")
    void dashboardPermissionCanReadOverview() throws Exception {
        when(dashboardService.getOverview(null)).thenReturn((DashboardOverviewResponse) null);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk());

        verify(dashboardService).getOverview(null);
    }
}
