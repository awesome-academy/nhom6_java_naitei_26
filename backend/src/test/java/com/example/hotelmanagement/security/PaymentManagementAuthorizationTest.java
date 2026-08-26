package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.payment.PaymentDetailResponse;
import com.example.hotelmanagement.dto.payment.PaymentListResponse;
import com.example.hotelmanagement.services.PaymentManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentManagementAuthorizationTest {

    private static final String PAYMENT_CODE = "PAY-2026-0123456789ABCDEF0123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentManagementService paymentManagementService;

    @Test
    void managementEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/payments/{paymentCode}", PAYMENT_CODE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentManagementService);
    }

    @Test
    @WithMockUser(authorities = "booking:read_any")
    void unrelatedPermissionCannotManagePayments() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/payments/{paymentCode}/refunds", PAYMENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"reason\":\"OTHER\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentManagementService);
    }

    @Test
    @WithMockUser(authorities = "payment:manage")
    void paymentPermissionCanReadPaymentManagementData() throws Exception {
        when(paymentManagementService.listPayments(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn((PaymentListResponse) null);
        when(paymentManagementService.getPayment(PAYMENT_CODE)).thenReturn((PaymentDetailResponse) null);
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/payments/{paymentCode}", PAYMENT_CODE))
                .andExpect(status().isOk());

        verify(paymentManagementService).listPayments(any(), any(), any(), any(), any(), any(), any());
        verify(paymentManagementService).getPayment(PAYMENT_CODE);
    }
}
