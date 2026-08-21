package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.services.FolioChargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FolioChargeAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CREATE_REQUEST = """
            {
              "serviceItemCode": "MINIBAR_WATER",
              "quantity": 2.00
            }
            """;
    private static final String VOID_REQUEST = """
            {
              "reason": "Entered twice"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FolioChargeService folioChargeService;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(folioChargeService);
    }

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void bookingPermissionCannotManageFolioCharges() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(folioChargeService);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invoicePermissionAllowsReadingCharges() throws Exception {
        when(folioChargeService.getFolioCharges(BOOKING_PUBLIC_ID))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID))
                .andExpect(status().isOk());

        verify(folioChargeService).getFolioCharges(BOOKING_PUBLIC_ID);
    }

    @Test
    void invoicePermissionAllowsCreateAndVoidWithAuthenticatedStaffId() throws Exception {
        UserPrincipal principal = principal(42L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("invoice:issue"))
        );
        when(folioChargeService.createFolioCharge(eq(BOOKING_PUBLIC_ID), any(), eq(42L)))
                .thenReturn(response());
        when(folioChargeService.voidFolioCharge(eq(BOOKING_PUBLIC_ID), eq(100L), any(), eq(42L)))
                .thenReturn(response());

        mockMvc.perform(post("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID)
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/bookings/" + BOOKING_PUBLIC_ID + "/folio-charges/100"
                ));
        mockMvc.perform(patch(
                        "/api/bookings/{bookingPublicId}/folio-charges/{chargeId}/void",
                        BOOKING_PUBLIC_ID,
                        100L
                ).with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VOID_REQUEST))
                .andExpect(status().isOk());

        verify(folioChargeService).createFolioCharge(eq(BOOKING_PUBLIC_ID), any(), eq(42L));
        verify(folioChargeService).voidFolioCharge(eq(BOOKING_PUBLIC_ID), eq(100L), any(), eq(42L));
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invalidRequestIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/folio-charges", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Penalty",
                                  "quantity": 0,
                                  "unitPrice": 100000.00
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(folioChargeService);
    }

    private FolioChargeResponse response() {
        OffsetDateTime chargedAt = OffsetDateTime.of(
                2026, 8, 21, 8, 0, 0, 0, ZoneOffset.UTC
        );
        return new FolioChargeResponse(
                100L,
                BOOKING_PUBLIC_ID,
                "MINIBAR_WATER",
                "Minibar water",
                money("2.00"),
                money("30000.00"),
                money("60000.00"),
                money("0.00"),
                money("0.00"),
                money("0.00"),
                money("60000.00"),
                chargedAt,
                20L,
                false,
                null,
                null,
                null
        );
    }

    private UserPrincipal principal(Long id) {
        User user = User.builder()
                .publicId("22222222-2222-2222-2222-222222222222")
                .email("staff@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return UserPrincipal.from(user);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
