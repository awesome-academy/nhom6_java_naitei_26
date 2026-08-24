package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.services.PaymentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mockMvc.perform(createRequest())
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void unrelatedPermissionCannotCreatePayment() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal(42L),
                null,
                List.of(new SimpleGrantedAuthority("room:read"))
        );

        mockMvc.perform(createRequest().with(authentication(authentication)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    @Test
    void bookingCreatorPermissionCanCreatePayment() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal(42L),
                null,
                List.of(new SimpleGrantedAuthority("booking:create"))
        );
        when(paymentService.createPayment(
                eq(BOOKING_PUBLIC_ID),
                any(),
                eq("payment-attempt-001"),
                eq(42L)
        )).thenReturn(response());

        mockMvc.perform(createRequest().with(authentication(authentication)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/bookings/" + BOOKING_PUBLIC_ID + "/payments/PAY-2026-0123456789ABCDEF0123"
                ));

        verify(paymentService).createPayment(
                eq(BOOKING_PUBLIC_ID),
                any(),
                eq("payment-attempt-001"),
                eq(42L)
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest() {
        return post("/api/bookings/{bookingPublicId}/payments", BOOKING_PUBLIC_ID)
                .header("Idempotency-Key", "payment-attempt-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "method": "INTERNET_BANKING"
                        }
                        """);
    }

    private PaymentResponse response() {
        return new PaymentResponse(
                "PAY-2026-0123456789ABCDEF0123",
                BOOKING_PUBLIC_ID,
                PaymentMethod.INTERNET_BANKING,
                new BigDecimal("1250000.00"),
                "VND",
                PaymentStatus.PENDING,
                OffsetDateTime.of(2026, 8, 24, 8, 15, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 8, 24, 8, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    private UserPrincipal principal(Long id) {
        User user = User.builder()
                .publicId("22222222-2222-2222-2222-222222222222")
                .email("customer@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
