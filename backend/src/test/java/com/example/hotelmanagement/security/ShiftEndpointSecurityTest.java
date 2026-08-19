package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.ShiftAssignmentService;
import com.example.hotelmanagement.services.ShiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShiftService shiftService;

    @MockBean
    private ShiftAssignmentService shiftAssignmentService;

    @Test
    void shiftEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/shifts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(shiftService, shiftAssignmentService);
    }

    @Test
    @WithMockUser
    void shiftEndpointRejectsUserWithoutManagePermission() throws Exception {
        mockMvc.perform(get("/api/shifts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(shiftService, shiftAssignmentService);
    }

    @Test
    @WithMockUser(authorities = "shift:manage")
    void shiftEndpointAllowsManagePermission() throws Exception {
        when(shiftService.getShifts()).thenReturn(List.of());

        mockMvc.perform(get("/api/shifts"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(shiftService).getShifts();
        verifyNoInteractions(shiftAssignmentService);
    }

    @Test
    @WithMockUser
    void shiftAssignmentEndpointRejectsUserWithoutManagePermission() throws Exception {
        mockMvc.perform(get("/api/shift-assignments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(shiftService, shiftAssignmentService);
    }

    @Test
    @WithMockUser(authorities = "shift:manage")
    void shiftAssignmentEndpointAllowsManagePermission() throws Exception {
        when(shiftAssignmentService.getShiftAssignments()).thenReturn(List.of());

        mockMvc.perform(get("/api/shift-assignments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(shiftAssignmentService).getShiftAssignments();
        verifyNoInteractions(shiftService);
    }
}
