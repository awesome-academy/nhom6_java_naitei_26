package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.PaymentCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentCallbackAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentCallbackService paymentCallbackService;

    @Test
    void sepayCallbackDoesNotRequireJwtAuthentication() throws Exception {
        mockMvc.perform(post("/api/payments/callback/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultCode\":0}"))
                .andExpect(status().isOk());

        verify(paymentCallbackService).handleCallback(
                eq("sepay"),
                any(),
                anyString()
        );
    }
}
