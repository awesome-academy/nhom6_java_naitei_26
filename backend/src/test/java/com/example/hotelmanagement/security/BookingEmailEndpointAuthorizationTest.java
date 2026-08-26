package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.BookingEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingEmailEndpointAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingEmailService bookingEmailService;

    @Test
    void bookingEmailEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/emails", BOOKING_PUBLIC_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/emails", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Hello\",\"body\":\"Welcome\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(bookingEmailService);
    }

    @Test
    @WithMockUser(authorities = "booking:read_any")
    void unrelatedPermissionCannotSendBookingEmails() throws Exception {
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/emails", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Hello\",\"body\":\"Welcome\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookingEmailService);
    }
}
