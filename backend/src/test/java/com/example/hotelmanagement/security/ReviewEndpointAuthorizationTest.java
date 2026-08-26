package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.review.ReviewListResponse;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.services.ReviewService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewEndpointAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final Long CUSTOMER_USER_ID = 88L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    void getReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/review", BOOKING_PUBLIC_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService);
    }

    @Test
    void unrelatedPermissionCannotReadReview() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/review", BOOKING_PUBLIC_ID)
                        .with(authentication(authenticationWith("booking:read_own"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    @Test
    void reviewCreatePermissionCanReadOwnReview() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/review", BOOKING_PUBLIC_ID)
                        .with(authentication(authenticationWith("review:create"))))
                .andExpect(status().isOk());

        verify(reviewService).getReview(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID);
    }

    @Test
    void reviewModeratePermissionCanListReviews() throws Exception {
        verifyNoInteractions(reviewService);
        org.mockito.Mockito.when(reviewService.listReviews(null, null, null, 0, 20))
                .thenReturn(new ReviewListResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/reviews")
                        .with(authentication(authenticationWith("review:moderate"))))
                .andExpect(status().isOk());

        verify(reviewService).listReviews(null, null, null, 0, 20);
    }

    @Test
    void reviewCreatePermissionCannotListReviews() throws Exception {
        mockMvc.perform(get("/api/admin/reviews")
                        .with(authentication(authenticationWith("review:create"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    @Test
    void reviewCreatePermissionCannotModerateReviews() throws Exception {
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/review/moderate", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}")
                        .with(authentication(authenticationWith("review:create"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    @Test
    void reviewCreatePermissionCannotReplyToReviews() throws Exception {
        mockMvc.perform(post("/api/bookings/{bookingPublicId}/review/reply", BOOKING_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffReply\":\"Thanks\"}")
                        .with(authentication(authenticationWith("review:create"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    private UsernamePasswordAuthenticationToken authenticationWith(String authority) {
        User user = User.builder()
                .publicId("22222222-2222-2222-2222-222222222222")
                .email("customer@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(CUSTOMER_USER_ID);
        return UsernamePasswordAuthenticationToken.authenticated(
                UserPrincipal.from(user),
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
