package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.services.CancellationPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancellationPolicyAuthorizationTest {

    private static final String POLICY_CODE = "FLEXIBLE";
    private static final String CREATE_REQUEST = """
        {
          "code": "FLEXIBLE",
          "name": "Flexible",
          "noShowChargePercent": 100.00,
          "isDefault": true,
          "rules": [
            {"minHoursBefore": 72, "refundPercent": 100.00},
            {"minHoursBefore": 0, "refundPercent": 0.00}
          ]
        }
        """;
    private static final String UPDATE_REQUEST = """
        {
          "name": "Flexible updated",
          "noShowChargePercent": 100.00,
          "isDefault": true,
          "isActive": true,
          "rules": [
            {"minHoursBefore": 48, "refundPercent": 100.00},
            {"minHoursBefore": 0, "refundPercent": 0.00}
          ]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CancellationPolicyService cancellationPolicyService;

    @Test
    void cancellationPolicyEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/cancellation-policies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cancellation-policies/active"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cancellationPolicyService);
    }

    @Test
    @WithMockUser(authorities = "booking:create")
    void bookingPermissionCanReadActivePoliciesButCannotManageThem() throws Exception {
        CancellationPolicyResponse response = response();
        when(cancellationPolicyService.getActiveCancellationPolicies())
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/cancellation-policies/active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cancellation-policies"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cancellation-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isForbidden());

        verify(cancellationPolicyService).getActiveCancellationPolicies();
        verifyNoMoreInteractions(cancellationPolicyService);
    }

    @Test
    @WithMockUser(authorities = "policy:manage")
    void policyPermissionAllowsCrudOperations() throws Exception {
        CancellationPolicyResponse response = response();
        when(cancellationPolicyService.getActiveCancellationPolicies()).thenReturn(List.of(response));
        when(cancellationPolicyService.getCancellationPolicies()).thenReturn(List.of(response));
        when(cancellationPolicyService.getCancellationPolicy(POLICY_CODE)).thenReturn(response);
        when(cancellationPolicyService.createCancellationPolicy(any())).thenReturn(response);
        when(cancellationPolicyService.updateCancellationPolicy(eq(POLICY_CODE), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/cancellation-policies/active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cancellation-policies"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cancellation-policies/{code}", POLICY_CODE))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cancellation-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/cancellation-policies/FLEXIBLE"
                ));
        mockMvc.perform(put("/api/cancellation-policies/{code}", POLICY_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_REQUEST))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/cancellation-policies/{code}", POLICY_CODE))
                .andExpect(status().isNoContent());

        verify(cancellationPolicyService).getActiveCancellationPolicies();
        verify(cancellationPolicyService).getCancellationPolicies();
        verify(cancellationPolicyService).getCancellationPolicy(POLICY_CODE);
        verify(cancellationPolicyService).createCancellationPolicy(any());
        verify(cancellationPolicyService).updateCancellationPolicy(eq(POLICY_CODE), any());
        verify(cancellationPolicyService).deleteCancellationPolicy(POLICY_CODE);
    }

    @Test
    @WithMockUser(authorities = "policy:manage")
    void invalidPolicyRequestIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/cancellation-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "code": "FLEXIBLE",
                              "name": "Flexible",
                              "noShowChargePercent": 100.00,
                              "rules": []
                            }
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cancellationPolicyService);
    }

    private CancellationPolicyResponse response() {
        return new CancellationPolicyResponse(
                POLICY_CODE,
                "Flexible",
                null,
                new BigDecimal("100.00"),
                true,
                true,
                List.of(),
                null,
                null
        );
    }
}
