package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.pricing.RateOverrideCreateRequest;
import com.example.hotelmanagement.dto.pricing.RateOverrideResponse;
import com.example.hotelmanagement.dto.pricing.RoomTypeRateOverrideCreateRequest;
import com.example.hotelmanagement.exceptions.RateOverrideConflictException;
import com.example.hotelmanagement.services.RateOverrideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateOverrideEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateOverrideService rateOverrideService;

    @Test
    void rateOverrideEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rate-overrides"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(rateOverrideService);
    }

    @Test
    @WithMockUser
    void rateOverrideEndpointRejectsUserWithoutPricingPermission() throws Exception {
        mockMvc.perform(get("/api/rate-overrides"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(rateOverrideService);
    }

    @Test
    @WithMockUser(authorities = "pricing:manage")
    void rateOverrideEndpointAllowsPricingPermission() throws Exception {
        when(rateOverrideService.getActiveRateOverrides()).thenReturn(List.of());

        mockMvc.perform(get("/api/rate-overrides"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(rateOverrideService).getActiveRateOverrides();
    }

    @Test
    @WithMockUser(authorities = "pricing:manage")
    void rateOverrideEndpointReturnsConflictForOverlappingRule() throws Exception {
        when(rateOverrideService.createRateOverride(any(RateOverrideCreateRequest.class)))
                .thenThrow(new RateOverrideConflictException(99L));

        mockMvc.perform(post("/api/rate-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomTypeId": 20,
                                  "name": "Weekend",
                                  "startDate": "2026-08-21",
                                  "endDate": "2026-08-25",
                                  "price": 1200000.00,
                                  "weekdays": [6, 7],
                                  "priority": 5
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Rate override conflicts with active rate override: 99"
                ));
    }

    @Test
    void roomTypeRateOverrideEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/rate-overrides/room-types/DLX")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomTypeCreateRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(rateOverrideService);
    }

    @Test
    @WithMockUser
    void roomTypeRateOverrideEndpointRejectsUserWithoutPricingPermission() throws Exception {
        mockMvc.perform(post("/api/rate-overrides/room-types/DLX")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomTypeCreateRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(rateOverrideService);
    }

    @Test
    @WithMockUser(authorities = "pricing:manage")
    void roomTypeRateOverrideEndpointCreatesRuleByPublicCode() throws Exception {
        RateOverrideResponse response = new RateOverrideResponse(
                88L,
                "DLX",
                "Deluxe",
                null,
                "Weekend",
                java.time.LocalDate.of(2026, 8, 21),
                java.time.LocalDate.of(2026, 8, 25),
                new java.math.BigDecimal("1200000.00"),
                List.of(6, 7),
                5,
                true,
                null,
                null
        );
        when(rateOverrideService.createRoomTypeRateOverride(
                anyString(),
                any(RoomTypeRateOverrideCreateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/rate-overrides/room-types/dlx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomTypeCreateRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rate-overrides/88"))
                .andExpect(jsonPath("$.roomTypeCode").value("DLX"))
                .andExpect(jsonPath("$.roomTypeName").value("Deluxe"))
                .andExpect(jsonPath("$.roomTypeId").doesNotExist());
    }

    private String roomTypeCreateRequestJson() {
        return """
                {
                  "name": "Weekend",
                  "startDate": "2026-08-21",
                  "endDate": "2026-08-25",
                  "price": 1200000.00,
                  "weekdays": [6, 7],
                  "priority": 5
                }
                """;
    }
}
