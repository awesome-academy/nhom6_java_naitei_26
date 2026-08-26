package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.ShiftAssignmentService;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffShiftAssignmentEndpointAuthorizationTest {

    private static final String PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShiftAssignmentService shiftAssignmentService;

    @Test
    void ownScheduleRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/staff/shift-assignments")
                        .param("from", "2026-08-24")
                        .param("to", "2026-09-06"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shiftAssignmentService);
    }

    @Test
    void ownScheduleRequiresOwnReadPermission() throws Exception {
        mockMvc.perform(get("/api/staff/shift-assignments")
                        .param("from", "2026-08-24")
                        .param("to", "2026-09-06")
                        .with(authentication(authenticationWith("shift:manage"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(shiftAssignmentService);
    }

    @Test
    void ownScheduleAcceptsOwnReadPermission() throws Exception {
        mockMvc.perform(get("/api/staff/shift-assignments")
                        .param("from", "2026-08-24")
                        .param("to", "2026-09-06")
                        .with(authentication(authenticationWith("shift:read_own"))))
                .andExpect(status().isOk());
    }

    @Test
    void ownMutationRequiresUpdatePermission() throws Exception {
        mockMvc.perform(post("/api/staff/shift-assignments/{publicId}/complete", PUBLIC_ID)
                        .with(authentication(authenticationWith("shift:read_own"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(shiftAssignmentService);
    }

    @Test
    void ownAbsenceRequiresUpdatePermission() throws Exception {
        mockMvc.perform(post("/api/staff/shift-assignments/{publicId}/absent", PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Sick leave\"}")
                        .with(authentication(authenticationWith("shift:read_own"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(shiftAssignmentService);
    }

    private UsernamePasswordAuthenticationToken authenticationWith(String authority) {
        User user = User.builder()
                .publicId("22222222-2222-2222-2222-222222222222")
                .email("staff@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(99L);
        return UsernamePasswordAuthenticationToken.authenticated(
                UserPrincipal.from(user),
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
